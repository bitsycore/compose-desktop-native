package apidemo

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSString
import platform.Security.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// macOS: Ktor bundles an OpenSSL-backed libcurl, which reads PEM / DER / PKCS#12
// certificate and key files directly — no certificate-store dance needed.

/** Point libcurl straight at the certificate / key files. */
actual fun prepareClientCert(inReq: ApiRequest): PreparedCert =
    PreparedCert(
        sslCert = inReq.certPath,
        sslCertType = inReq.certFormat.curlName,
        sslKey = inReq.keyPath.ifBlank { null },
        sslKeyType = if (inReq.keyPath.isNotBlank()) inReq.keyFormat.curlName else null,
        keyPassword = inReq.certPassword.ifEmpty { null },
        cleanup = {},
    )

/** No temporary certificate store off Windows. */
actual fun sweepTempClientCerts() {}

/** Continue the server's chain with intermediates / roots pulled from the
macOS Keychain — same UX as the Windows path does via the trust store.
Falls back to the name-only placeholder if Apple's APIs can't resolve
the chain (e.g. the server's leaf doesn't parse, or its issuer isn't
in any keychain). */
@OptIn(ExperimentalForeignApi::class)
actual fun extendChain(inServerCerts: List<List<Pair<String, String>>>): List<ChainCert> {
    val vLeafPem = inServerCerts.firstOrNull()?.let { certFieldOf(it, "Cert") }
        ?: return serverChainWithIssuerName(inServerCerts)
    val vOs = runCatching { osChainApple(vLeafPem, inServerCerts.size, inServerCerts) }.getOrNull()
    return if (!vOs.isNullOrEmpty()) vOs else serverChainWithIssuerName(inServerCerts)
}

// ============
//  Chain walk via Security.framework
// ============

/** Build the chain by handing the leaf to SecTrust + evaluating against
an SSL policy — Apple then walks Keychain, pulling intermediates and
the root in. Returns the resolved chain with fromServer=true for the
first inServerCount entries (those came down on the wire) and false
for anything the OS added. */
@OptIn(ExperimentalForeignApi::class)
private fun osChainApple(
    inLeafPem: String,
    inServerCount: Int,
    inServerCerts: List<List<Pair<String, String>>>,
): List<ChainCert> {
    // Parse every cert the server presented, not just the leaf — Apple's
    // chain builder uses them as candidate intermediates when stitching to
    // a root, instead of relying solely on AIA-fetch which is off by default.
    val vServerCerts = inServerCerts.mapNotNull {
        certFieldOf(it, "Cert")?.let { vPem -> derFromPem(vPem) }
    }
    if (vServerCerts.isEmpty()) return emptyList()

    val vCertRefs = vServerCerts.map { createSecCertificate(it) ?: return emptyList() }
    val vCertArray = createCFArray(vCertRefs)

    // SSL policy lets evaluateTrust pull a root out of Keychain.
    val vPolicy = SecPolicyCreateSSL(true, null) ?: run {
        vCertRefs.forEach { CFRelease(it) }
        CFRelease(vCertArray)
        return emptyList()
    }

    val vTrustVar = nativeHeap.alloc<SecTrustRefVar>()
    val vCreateStatus = SecTrustCreateWithCertificates(vCertArray, vPolicy, vTrustVar.ptr)
    CFRelease(vPolicy)
    if (vCreateStatus != errSecSuccess || vTrustVar.value == null) {
        vCertRefs.forEach { CFRelease(it) }
        CFRelease(vCertArray)
        nativeHeap.free(vTrustVar)
        return emptyList()
    }
    val vTrust = vTrustVar.value!!

    // Evaluate even if it fails — we still want the partial chain back so
    // the UI can show what it could resolve. SecTrustEvaluateWithError
    // stops at the highest cert it found.
    val vErr = nativeHeap.alloc<CFErrorRefVar>()
    SecTrustEvaluateWithError(vTrust, vErr.ptr)
    vErr.value?.let { CFRelease(it) }
    nativeHeap.free(vErr)

    val vCount = SecTrustGetCertificateCount(vTrust).toInt()
    val vResolved = ArrayList<ChainCert>(vCount)
    val vSubjects = ArrayList<String>(vCount)

    for (vI in 0 until vCount) {
        val vCert = SecTrustGetCertificateAtIndex(vTrust, vI.convert()) ?: continue
        val vSubject = secSubjectSummary(vCert)
        vSubjects.add(vSubject ?: "")
    }
    for (vI in 0 until vCount) {
        val vCert = SecTrustGetCertificateAtIndex(vTrust, vI.convert()) ?: continue
        val vSubject = vSubjects[vI].ifBlank { null }
        // The issuer is the next cert in the chain's subject (or this
        // cert's own subject if it's the self-signed root).
        val vIssuer = when {
            vI + 1 < vCount -> vSubjects[vI + 1].ifBlank { null }
            vSubject != null -> vSubject // root: self-signed
            else -> null
        }
        val vPem = secCertPem(vCert)
        val vFields = buildList {
            vSubject?.let { add("Subject" to "CN=$it") }
            vIssuer?.let { add("Issuer" to "CN=$it") }
            vPem?.let { add("Cert" to it) }
        }
        vResolved.add(ChainCert(vFields, vI < inServerCount))
    }

    CFRelease(vTrust)
    vCertRefs.forEach { CFRelease(it) }
    CFRelease(vCertArray)
    nativeHeap.free(vTrustVar)
    return vResolved
}

/** SecCertificate from DER bytes. Returns null if the bytes aren't a
valid X.509 cert. */
@OptIn(ExperimentalForeignApi::class)
private fun createSecCertificate(inDer: ByteArray): SecCertificateRef? {
    val vData = inDer.usePinned { vPin ->
        CFDataCreate(null, vPin.addressOf(0).reinterpret(), inDer.size.convert())
    } ?: return null
    val vCert = SecCertificateCreateWithData(null, vData)
    CFRelease(vData)
    return vCert
}

/** CFArray of SecCertificateRef — the input to SecTrustCreateWithCertificates. */
@OptIn(ExperimentalForeignApi::class)
private fun createCFArray(inCerts: List<SecCertificateRef>): CFArrayRef {
    val vPtrs = nativeHeap.allocArray<COpaquePointerVar>(inCerts.size)
    for (vI in inCerts.indices) vPtrs[vI] = inCerts[vI]
    val vArr = CFArrayCreate(null, vPtrs, inCerts.size.convert(), kCFTypeArrayCallBacks.ptr)!!
    nativeHeap.free(vPtrs)
    return vArr
}

/** Apple's "subject summary" — basically the CN, but falls back to the
organisation when no CN is present (matches what Keychain Access
displays in its cert list). */
@OptIn(ExperimentalForeignApi::class)
private fun secSubjectSummary(inCert: SecCertificateRef): String? {
    val vCf = SecCertificateCopySubjectSummary(inCert) ?: return null
    val vNs = CFBridgingRelease(vCf) as? NSString
    return vNs?.toString()
}

/** PEM string for a SecCertificate — base64 of the DER bytes wrapped
with -----BEGIN CERTIFICATE-----/-----END----- headers. */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private fun secCertPem(inCert: SecCertificateRef): String? {
    val vData = SecCertificateCopyData(inCert) ?: return null
    val vLen = CFDataGetLength(vData).toInt()
    val vBytes = ByteArray(vLen)
    val vPtr = CFDataGetBytePtr(vData)
    if (vPtr != null) for (vI in 0 until vLen) vBytes[vI] = vPtr[vI].toByte()
    CFRelease(vData)
    val vB64 = Base64.encode(vBytes)
    val vWrapped = vB64.chunked(64).joinToString("\n")
    return "-----BEGIN CERTIFICATE-----\n$vWrapped\n-----END CERTIFICATE-----"
}

/** Decode a PEM block to DER bytes, or pass DER bytes through. */
@OptIn(ExperimentalEncodingApi::class)
private fun derFromPem(inPem: String): ByteArray? {
    if (!inPem.contains("-----BEGIN")) return inPem.encodeToByteArray()
    val vBody = inPem.lineSequence()
        .filterNot { it.startsWith("-----") }
        .joinToString("") { it.trim() }
    return runCatching { Base64.decode(vBody) }.getOrNull()
}
