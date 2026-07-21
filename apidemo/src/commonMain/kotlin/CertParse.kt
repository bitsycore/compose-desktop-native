package apidemo

import kotlin.io.encoding.Base64

// ==================
// MARK: Minimal X.509 / DER parser
// ==================
// Some TLS backends (notably Schannel on Windows) expose only Subject / Issuer /
// the PEM through CURLINFO_CERTINFO — not the parsed fields (validity, serial,
// SAN, algorithms). Since every backend gives us the PEM, we parse it ourselves
// to fill in the detail. Just enough ASN.1 to walk a Certificate; anything odd
// is swallowed and the field is simply omitted.

/** Parsed certificate detail, all nullable/empty when not found or unparseable. */
class CertDetails(
    val version: Int?,                 // X.509 version (1/2/3)
    val serial: String?,               // colon-separated hex
    val sigAlg: String?,               // signature algorithm (friendly name or OID)
    val keyAlg: String?,               // public-key algorithm (friendly name or OID)
    val notBefore: String?,            // "YYYY-MM-DD HH:MM:SS UTC"
    val notAfter: String?,
    val sans: List<String>,            // dNSName SANs
)

// ============
//  DER reader: each next() returns [tag, contentStart, contentEnd] and advances.
private class DerReader(val b: ByteArray, var pos: Int, val end: Int) {
    fun hasMore() = pos < end
    fun next(): IntArray {
        val vTag = b[pos++].toInt() and 0xFF
        var vLen = b[pos++].toInt() and 0xFF
        if (vLen and 0x80 != 0) {
            val vN = vLen and 0x7F
            vLen = 0
            repeat(vN) { vLen = (vLen shl 8) or (b[pos++].toInt() and 0xFF) }
        }
        val vStart = pos
        pos += vLen
        return intArrayOf(vTag, vStart, vStart + vLen)
    }
}

/** Parse the PEM into the fields the chain dialog shows. Returns null on any
structural surprise (the UI just falls back to whatever the backend gave). */
fun parseCertDetails(inPem: String): CertDetails? {
    val vDer = pemToDer(inPem) ?: return null
    return try {
        val vCert = DerReader(vDer, 0, vDer.size).next()                 // Certificate SEQUENCE
        val vCertInner = DerReader(vDer, vCert[1], vCert[2])
        val vTbs = vCertInner.next()                                     // tbsCertificate
        val vSigAlg = firstOidName(vDer, vCertInner.next())              // signatureAlgorithm

        val vT = DerReader(vDer, vTbs[1], vTbs[2])
        var vE = vT.next()
        var vVersion: Int? = null
        if (vE[0] == 0xA0)                                               // [0] EXPLICIT version
        {
            val vVi = DerReader(vDer, vE[1], vE[2]).next()
            if (vVi[0] == 0x02 && vVi[2] > vVi[1]) vVersion = (vDer[vVi[1]].toInt() and 0xFF) + 1
            vE = vT.next()
        }
        val vSerial = if (vE[0] == 0x02) bytesToHex(vDer, vE[1], vE[2]) else null
        vT.next()                                                        // signature
        vT.next()                                                        // issuer
        val vValidity = vT.next()                                        // validity SEQUENCE
        val vVr = DerReader(vDer, vValidity[1], vValidity[2])
        val vNotBefore = parseAsn1Time(vDer, vVr.next())
        val vNotAfter = parseAsn1Time(vDer, vVr.next())
        vT.next()                                                        // subject
        val vSpki = vT.next()                                            // subjectPublicKeyInfo SEQUENCE
        // SPKI = SEQUENCE { algorithm AlgorithmIdentifier SEQUENCE { OID … }, key BIT STRING }
        val vKeyAlg = firstOidName(vDer, DerReader(vDer, vSpki[1], vSpki[2]).next())
        val vSans = ArrayList<String>()
        while (vT.hasMore()) {
            val vX = vT.next()
            if (vX[0] == 0xA3) parseSans(vDer, vX, vSans)                // extensions [3]
        }
        CertDetails(vVersion, vSerial, vSigAlg, vKeyAlg, vNotBefore, vNotAfter, vSans)
    } catch (_: Throwable) {
        null
    }
}

// ============
//  Helpers

private fun pemToDer(inPem: String): ByteArray? {
    val vBody = inPem.lineSequence().filterNot { it.contains("-----") }.joinToString("") { it.trim() }
    if (vBody.isBlank()) return null
    return try {
        Base64.decode(vBody)
    } catch (_: Throwable) {
        null
    }
}

/** The friendly name (or dotted OID) of the first child OID of a SEQUENCE — used
for signatureAlgorithm and subjectPublicKeyInfo's AlgorithmIdentifier. */
private fun firstOidName(inB: ByteArray, inSeq: IntArray): String? {
    val vR = DerReader(inB, inSeq[1], inSeq[2])
    val vOid = vR.next()
    if (vOid[0] != 0x06) return null
    return oidName(oidToString(inB, vOid[1], vOid[2]))
}

/** Pull dNSName entries out of the SubjectAltName extension under [3]. */
private fun parseSans(inB: ByteArray, inExt3: IntArray, inOut: MutableList<String>) {
    val vSeq = DerReader(inB, inExt3[1], inExt3[2]).next()               // SEQUENCE OF Extension
    val vEs = DerReader(inB, vSeq[1], vSeq[2])
    while (vEs.hasMore()) {
        val vExtn = vEs.next()
        val vEr = DerReader(inB, vExtn[1], vExtn[2])
        val vOid = vEr.next()                                           // extnID
        // SAN OID 2.5.29.17 == bytes 55 1D 11
        val vIsSan = vOid[2] - vOid[1] == 3 &&
                (inB[vOid[1]].toInt() and 0xFF) == 0x55 &&
                (inB[vOid[1] + 1].toInt() and 0xFF) == 0x1D &&
                (inB[vOid[1] + 2].toInt() and 0xFF) == 0x11
        var vVal = vEr.next()
        if (vVal[0] == 0x01) vVal = vEr.next()                          // skip critical BOOLEAN
        if (vIsSan && vVal[0] == 0x04)                                  // OCTET STRING wrapping GeneralNames
        {
            val vGnSeq = DerReader(inB, vVal[1], vVal[2]).next()
            val vGn = DerReader(inB, vGnSeq[1], vGnSeq[2])
            while (vGn.hasMore()) {
                val vName = vGn.next()
                if (vName[0] == 0x82) inOut.add(inB.copyOfRange(vName[1], vName[2]).decodeToString())  // [2] dNSName
            }
        }
    }
}

/** ASN.1 UTCTime (YYMMDD…) / GeneralizedTime (YYYYMMDD…) → readable UTC string. */
private fun parseAsn1Time(inB: ByteArray, inTlv: IntArray): String? {
    val vS = inB.copyOfRange(inTlv[1], inTlv[2]).decodeToString()
    return try {
        val vYear: String
        val vRest: String
        if (inTlv[0] == 0x18)                                           // GeneralizedTime
        {
            vYear = vS.substring(0, 4); vRest = vS.substring(4)
        } else                                                            // UTCTime — RFC 5280 pivot at 50
        {
            val vYy = vS.substring(0, 2).toInt()
            vYear = (if (vYy >= 50) "19" else "20") + vS.substring(0, 2)
            vRest = vS.substring(2)
        }
        val vMo = vRest.substring(0, 2)
        val vDa = vRest.substring(2, 4)
        val vHh = vRest.substring(4, 6)
        val vMi = vRest.substring(6, 8)
        val vSs = if (vRest.length >= 10 && vRest[8].isDigit()) vRest.substring(8, 10) else "00"
        "$vYear-$vMo-$vDa $vHh:$vMi:$vSs UTC"
    } catch (_: Throwable) {
        vS
    }
}

/** Colon-separated hex of a byte range (serial number rendering). */
private fun bytesToHex(inB: ByteArray, inFrom: Int, inTo: Int): String =
    (inFrom until inTo).joinToString(":") { (inB[it].toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Decode an OID's content bytes to dotted-decimal. */
private fun oidToString(inB: ByteArray, inFrom: Int, inTo: Int): String {
    if (inTo <= inFrom) return ""
    val vSb = StringBuilder()
    val vFirst = inB[inFrom].toInt() and 0xFF
    vSb.append(vFirst / 40).append('.').append(vFirst % 40)
    var vAcc = 0L
    for (vI in (inFrom + 1) until inTo) {
        val vByte = inB[vI].toInt() and 0xFF
        vAcc = (vAcc shl 7) or (vByte and 0x7F).toLong()
        if (vByte and 0x80 == 0) {
            vSb.append('.').append(vAcc); vAcc = 0
        }
    }
    return vSb.toString()
}

/** Friendly name for the common signature / public-key OIDs, else the OID. */
private fun oidName(inOid: String): String = when (inOid) {
    "1.2.840.113549.1.1.1" -> "RSA"
    "1.2.840.113549.1.1.5" -> "SHA1withRSA"
    "1.2.840.113549.1.1.11" -> "SHA256withRSA"
    "1.2.840.113549.1.1.12" -> "SHA384withRSA"
    "1.2.840.113549.1.1.13" -> "SHA512withRSA"
    "1.2.840.113549.1.1.10" -> "RSASSA-PSS"
    "1.2.840.10045.2.1" -> "EC"
    "1.2.840.10045.4.3.2" -> "ECDSAwithSHA256"
    "1.2.840.10045.4.3.3" -> "ECDSAwithSHA384"
    "1.2.840.10045.4.3.4" -> "ECDSAwithSHA512"
    "1.3.101.112" -> "Ed25519"
    "1.3.101.113" -> "Ed448"
    else -> inOid
}
