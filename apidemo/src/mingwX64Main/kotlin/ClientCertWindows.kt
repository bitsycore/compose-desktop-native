package apidemo

import kotlinx.cinterop.*
import platform.windows.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ==================
// MARK: Windows client-certificate store import (Schannel mTLS)
// ==================
// curl's Schannel backend can only present a client certificate that lives in a
// Windows certificate store, referenced by SHA-1 thumbprint. So for a request
// that carries a certificate we import the cert + private key into the standard
// CurrentUser\MY ("Personal") store, hand curl "CurrentUser\MY\<thumbprint>",
// and delete it again the instant the request finishes — nothing accumulates.
//
// Our entries are tagged (friendly name + key-container name) with a unique
// prefix so the startup sweep can clear crash leftovers without ever touching
// the user's own certificates.
//
// Kotlin/Native maps several CryptoAPI LPCSTR params to String?, which makes the
// CryptDecodeObjectEx route (numeric struct-type sentinels) unusable — so the
// RSA private key is parsed here (ASN.1) and laid out as a CryptoAPI
// PRIVATEKEYBLOB directly, then handed to CryptImportKey. RSA (PKCS#1 or PKCS#8)
// and PKCS#12 are handled; EC PEM/DER is reported as unsupported with a hint.

private const val kStorePath = "CurrentUser\\MY"        // prefix handed to curl (before the thumbprint)
private const val kStoreName = "MY"                     // the system store we open
private const val kTagPrefix = "CompDeskNat_Apidemo_"   // friendly-name + container-name prefix
private const val kProvName = "Microsoft Enhanced Cryptographic Provider v1.0"
private const val kCryptUserKeyset = 0x00001000u        // CRYPT_USER_KEYSET (not in platform.windows)

private val kEncoding: UInt = X509_ASN_ENCODING.toUInt() or PKCS_7_ASN_ENCODING.toUInt()

/** Import the request's certificate into CurrentUser\MY and return a libcurl
   store reference plus a cleanup that removes it again. */
@OptIn(ExperimentalForeignApi::class)
actual fun prepareClientCert(inReq: ApiRequest): PreparedCert
	{
	val vCertBytes = readFileBytes(inReq.certPath)
	val (vThumb, vContainer) = when (inReq.certFormat)
		{
		CertFormat.PKCS12 -> importPkcs12(vCertBytes, inReq.certPassword)
		else              -> importPemDerRsa(vCertBytes, inReq)
		}
	return PreparedCert(
		sslCert     = "$kStorePath\\$vThumb",
		sslCertType = null,                 // store reference — not a file type
		sslKey      = null,
		sslKeyType  = null,
		keyPassword = null,
		cleanup     = { deleteTempCert(vThumb, vContainer) },
	)
	}

// ============
//  PEM / DER (RSA) → store
// ============

/** Build a cert context from PEM/DER, import its RSA private key into a named
   container, link the two, tag and add to CurrentUser\MY. Returns
   (thumbprintHex, containerName). */
@OptIn(ExperimentalForeignApi::class)
private fun importPemDerRsa(inCertBytes: ByteArray, inReq: ApiRequest): Pair<String, String?>
	{
	val vCertDer = derFromMaybePem(inCertBytes, "CERTIFICATE")
	val vKeyBytes = if (inReq.keyPath.isNotBlank()) readFileBytes(inReq.keyPath) else inCertBytes
	val vKeyDer = privateKeyDer(vKeyBytes)
		?: throw RuntimeException("No private key found — PEM/DER on Windows needs the RSA key (a key file, or a key block in the certificate file).")
	val vBlob = try { rsaKeyDerToBlob(vKeyDer) }
		catch (e: Throwable) { throw RuntimeException("Unsupported private key (RSA PKCS#1/PKCS#8 is supported; EC isn't yet). Convert to PKCS#12: openssl pkcs12 -export -inkey key.pem -in cert.pem -out client.p12") }

	val vCert = vCertDer.usePinned { vPin ->
		CertCreateCertificateContext(kEncoding, vPin.addressOf(0).reinterpret(), vCertDer.size.convert())
	} ?: winError("CertCreateCertificateContext")

	try
		{
		val vThumb = thumbprintHex(vCert)
		val vContainer = kTagPrefix + vThumb
		importRsaIntoContainer(vContainer, vBlob)
		try
			{
			linkKeyToCert(vCert, vContainer)
			tagFriendlyName(vCert, kTagPrefix + vThumb)
			addToStore(vCert)
			return vThumb to vContainer
			}
		catch (e: Throwable) { deleteContainer(vContainer); throw e }
		}
	finally { CertFreeCertificateContext(vCert) }
	}

/** Create a fresh named key container and import the RSA blob into it. */
@OptIn(ExperimentalForeignApi::class)
private fun importRsaIntoContainer(inContainer: String, inBlob: ByteArray) = memScoped {
	deleteContainer(inContainer)   // drop a stale one if it somehow exists
	val vProv = alloc<ULongVar>()
	if (CryptAcquireContextW(vProv.ptr, inContainer, kProvName, PROV_RSA_FULL.toUInt(), CRYPT_NEWKEYSET.toUInt()) == 0)
		winError("CryptAcquireContext(NEWKEYSET)")
	try
		{
		val vKey = alloc<ULongVar>()
		val vOk = inBlob.usePinned { vPin ->
			CryptImportKey(vProv.value, vPin.addressOf(0).reinterpret(), inBlob.size.convert(), 0uL, CRYPT_EXPORTABLE.toUInt(), vKey.ptr)
		}
		if (vOk == 0) winError("CryptImportKey")
		CryptDestroyKey(vKey.value)
		}
	finally { CryptReleaseContext(vProv.value, 0u) }
}

/** Attach the named container to the cert so Schannel can find its key. */
@OptIn(ExperimentalForeignApi::class)
private fun linkKeyToCert(inCert: CPointer<CERT_CONTEXT>, inContainer: String) = memScoped {
	val vInfo = alloc<CRYPT_KEY_PROV_INFO>()
	vInfo.pwszContainerName = inContainer.wcstr.ptr
	vInfo.pwszProvName = kProvName.wcstr.ptr
	vInfo.dwProvType = PROV_RSA_FULL.toUInt()
	vInfo.dwFlags = 0u
	vInfo.cProvParam = 0u
	vInfo.rgProvParam = null
	vInfo.dwKeySpec = AT_KEYEXCHANGE.toUInt()
	if (CertSetCertificateContextProperty(inCert, CERT_KEY_PROV_INFO_PROP_ID.toUInt(), 0u, vInfo.ptr) == 0)
		winError("CertSetCertificateContextProperty(KEY_PROV_INFO)")
}

// ============
//  RSA key ASN.1 → CryptoAPI PRIVATEKEYBLOB
// ============

/** Minimal DER reader for the RSA key structures. */
private class Der(val b: ByteArray)
	{
	var p = 0
	fun tag(): Int = b[p++].toInt() and 0xFF
	fun len(): Int
		{
		val vFirst = b[p++].toInt() and 0xFF
		if (vFirst and 0x80 == 0) return vFirst
		var vLen = 0
		repeat(vFirst and 0x7F) { vLen = (vLen shl 8) or (b[p++].toInt() and 0xFF) }
		return vLen
		}
	fun sequence() { require(tag() == 0x30) { "SEQUENCE expected" }; len() }
	fun peek(): Int = b[p].toInt() and 0xFF
	fun skip() { tag(); val vL = len(); p += vL }
	fun octetString(): ByteArray { require(tag() == 0x04) { "OCTET STRING expected" }; val vL = len(); return b.copyOfRange(p, p + vL).also { p += vL } }
	// An INTEGER's magnitude (big-endian, leading zero stripped).
	fun integer(): ByteArray
		{
		require(tag() == 0x02) { "INTEGER expected" }
		val vL = len(); val vV = b.copyOfRange(p, p + vL); p += vL
		var vS = 0; while (vS < vV.size - 1 && vV[vS].toInt() == 0) vS++
		return vV.copyOfRange(vS, vV.size)
		}
	}

/** Turn a PKCS#1 or PKCS#8 RSA private-key DER into a CryptoAPI PRIVATEKEYBLOB.
   Throws if it isn't an RSA key we can lay out. */
private fun rsaKeyDerToBlob(inDer: ByteArray): ByteArray
	{
	val vR = Der(inDer)
	vR.sequence(); vR.integer()           // outer version
	return if (vR.peek() == 0x30)
		{
		// PKCS#8 PrivateKeyInfo: skip AlgorithmIdentifier, parse the inner key.
		vR.skip()
		parsePkcs1(vR.octetString())
		}
	else
		// PKCS#1: n, e, d, p, q, dp, dq, iqmp follow the version we just read.
		buildBlob(vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer())
	}

private fun parsePkcs1(inDer: ByteArray): ByteArray
	{
	val vR = Der(inDer)
	vR.sequence(); vR.integer()           // version
	return buildBlob(vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer(), vR.integer())
	}

/** Lay out the MS PRIVATEKEYBLOB (all little-endian) from the RSA components. */
private fun buildBlob(
	inN: ByteArray, inE: ByteArray, inD: ByteArray,
	inP: ByteArray, inQ: ByteArray, inDp: ByteArray, inDq: ByteArray, inIq: ByteArray,
): ByteArray
	{
	val vMod = inN.size            // modulus length in bytes
	val vHalf = vMod / 2
	val vOut = ArrayList<Byte>(20 + vMod * 2 + vHalf * 5)
	vOut.add(PRIVATEKEYBLOB.toByte()); vOut.add(CUR_BLOB_VERSION.toByte()); vOut.add(0); vOut.add(0)  // BLOBHEADER
	vOut.addAll(le32(CALG_RSA_KEYX.toUInt()).toList())
	vOut.addAll(le32(0x32415352u).toList())             // "RSA2"
	vOut.addAll(le32((vMod * 8).toUInt()).toList())      // bitlen
	vOut.addAll(le32(beToUInt(inE)).toList())            // pubexp
	vOut.addAll(leField(inN, vMod).toList())
	vOut.addAll(leField(inP, vHalf).toList())
	vOut.addAll(leField(inQ, vHalf).toList())
	vOut.addAll(leField(inDp, vHalf).toList())
	vOut.addAll(leField(inDq, vHalf).toList())
	vOut.addAll(leField(inIq, vHalf).toList())
	vOut.addAll(leField(inD, vMod).toList())
	return vOut.toByteArray()
	}

/** A big-endian magnitude reversed to little-endian and zero-padded to inSize. */
private fun leField(inBe: ByteArray, inSize: Int): ByteArray
	{
	val vOut = ByteArray(inSize)
	for (vI in inBe.indices) if (vI < inSize) vOut[vI] = inBe[inBe.size - 1 - vI]
	return vOut
	}

private fun le32(inV: UInt): ByteArray =
	byteArrayOf((inV and 0xFFu).toByte(), ((inV shr 8) and 0xFFu).toByte(), ((inV shr 16) and 0xFFu).toByte(), ((inV shr 24) and 0xFFu).toByte())

private fun beToUInt(inBe: ByteArray): UInt
	{
	var vV = 0u
	for (vB in inBe) vV = (vV shl 8) or (vB.toInt() and 0xFF).toUInt()
	return vV
	}

// ============
//  PKCS#12 → store
// ============

/** Import a .p12/.pfx into a memory store, then copy its cert (with the key
   container PFXImportCertStore created) into CurrentUser\MY. */
@OptIn(ExperimentalForeignApi::class)
private fun importPkcs12(inBytes: ByteArray, inPassword: String): Pair<String, String?> = memScoped {
	inBytes.usePinned { vPin ->
		val vBlob = alloc<CRYPT_INTEGER_BLOB>()
		vBlob.cbData = inBytes.size.convert()
		vBlob.pbData = vPin.addressOf(0).reinterpret()
		val vMem = PFXImportCertStore(vBlob.ptr, inPassword, kCryptUserKeyset or CRYPT_EXPORTABLE.toUInt())
			?: winError("PFXImportCertStore (wrong password, or not a PKCS#12 file?)")
		try
			{
			val vSrc = CertEnumCertificatesInStore(vMem, null) ?: throw RuntimeException("PKCS#12 contained no certificate.")
			val vThumb = thumbprintHex(vSrc)
			val vContainer = containerNameOf(vSrc)
			tagFriendlyName(vSrc, kTagPrefix + vThumb)
			addToStore(vSrc)
			vThumb to vContainer
			}
		finally { CertCloseStore(vMem, 0u) }
	}
}

/** The key-container name PFXImportCertStore assigned to a cert (from its
   CERT_KEY_PROV_INFO), so cleanup can delete it. */
@OptIn(ExperimentalForeignApi::class)
private fun containerNameOf(inCert: CPointer<CERT_CONTEXT>): String? = memScoped {
	val vLen = alloc<UIntVar>()
	if (CertGetCertificateContextProperty(inCert, CERT_KEY_PROV_INFO_PROP_ID.toUInt(), null, vLen.ptr) == 0) return@memScoped null
	val vBuf = allocArray<ByteVar>(vLen.value.toInt())
	if (CertGetCertificateContextProperty(inCert, CERT_KEY_PROV_INFO_PROP_ID.toUInt(), vBuf, vLen.ptr) == 0) return@memScoped null
	vBuf.reinterpret<CRYPT_KEY_PROV_INFO>().pointed.pwszContainerName?.toKStringFromUtf16()
}

// ============
//  Shared store helpers
// ============

/** Tag a cert with our friendly-name prefix so the startup sweep recognises it. */
@OptIn(ExperimentalForeignApi::class)
private fun tagFriendlyName(inCert: CPointer<CERT_CONTEXT>, inName: String) = memScoped {
	val vBlob = alloc<CRYPT_INTEGER_BLOB>()
	vBlob.cbData = ((inName.length + 1) * 2).convert()
	vBlob.pbData = inName.wcstr.ptr.reinterpret()
	CertSetCertificateContextProperty(inCert, CERT_FRIENDLY_NAME_PROP_ID.toUInt(), 0u, vBlob.ptr)
}

/** Add a cert context to CurrentUser\MY (replacing any same-thumbprint entry). */
@OptIn(ExperimentalForeignApi::class)
private fun addToStore(inCert: CPointer<CERT_CONTEXT>)
	{
	val vStore = openMyStore() ?: winError("CertOpenSystemStore(MY)")
	try
		{
		if (CertAddCertificateContextToStore(vStore, inCert, CERT_STORE_ADD_REPLACE_EXISTING.toUInt(), null) == 0)
			winError("CertAddCertificateContextToStore")
		}
	finally { CertCloseStore(vStore, 0u) }
	}

/** Open CurrentUser\MY (the current user's Personal store). */
@OptIn(ExperimentalForeignApi::class)
private fun openMyStore() = CertOpenSystemStoreW(0.convert(), kStoreName)

/** The SHA-1 thumbprint of a cert as uppercase hex. */
@OptIn(ExperimentalForeignApi::class)
private fun thumbprintHex(inCert: CPointer<CERT_CONTEXT>): String = memScoped {
	val vLen = alloc<UIntVar>().apply { value = 20u }
	val vBuf = allocArray<UByteVar>(20)
	if (CertGetCertificateContextProperty(inCert, CERT_SHA1_HASH_PROP_ID.toUInt(), vBuf, vLen.ptr) == 0)
		winError("CertGetCertificateContextProperty(SHA1)")
	buildString { for (vI in 0 until vLen.value.toInt()) append(vBuf[vI].toInt().toString(16).uppercase().padStart(2, '0')) }
}

/** Remove a temporary cert (by thumbprint) from CurrentUser\MY and delete its
   key container. Called per-request in finally; ignores what's already gone. */
@OptIn(ExperimentalForeignApi::class)
private fun deleteTempCert(inThumbHex: String, inContainer: String?)
	{
	val vStore = openMyStore()
	if (vStore != null) memScoped {
		val vHash = hexToBytes(inThumbHex)
		val vBuf = allocArray<UByteVar>(vHash.size)
		for (vI in vHash.indices) vBuf[vI] = vHash[vI].toUByte()
		val vBlob = alloc<CRYPT_INTEGER_BLOB>()
		vBlob.cbData = vHash.size.convert()
		vBlob.pbData = vBuf
		val vFound = CertFindCertificateInStore(vStore, kEncoding, 0u, CERT_FIND_SHA1_HASH.toUInt(), vBlob.ptr, null)
		if (vFound != null) CertDeleteCertificateFromStore(vFound)   // also frees the context
		CertCloseStore(vStore, 0u)
	}
	if (inContainer != null) deleteContainer(inContainer)
	}

/** Delete a named key container (best-effort). */
@OptIn(ExperimentalForeignApi::class)
private fun deleteContainer(inContainer: String) = memScoped {
	val vProv = alloc<ULongVar>()
	CryptAcquireContextW(vProv.ptr, inContainer, kProvName, PROV_RSA_FULL.toUInt(), CRYPT_DELETEKEYSET.toUInt())
}

/** Remove every temp cert we ever added (prefix-tagged) plus its container — for
   clearing crash leftovers at startup. The user's own certs are never matched. */
@OptIn(ExperimentalForeignApi::class)
actual fun sweepTempClientCerts()
	{
	val vStore = openMyStore() ?: return
	val vDoomed = mutableListOf<Pair<String, String?>>()   // thumbprint, container
	var vCert = CertEnumCertificatesInStore(vStore, null)
	while (vCert != null)
		{
		val vFriendly = friendlyNameOf(vCert)
		if (vFriendly != null && vFriendly.startsWith(kTagPrefix))
			vDoomed.add(thumbprintHex(vCert) to containerNameOf(vCert))
		vCert = CertEnumCertificatesInStore(vStore, vCert)
		}
	CertCloseStore(vStore, 0u)
	vDoomed.forEach { deleteTempCert(it.first, it.second) }
	}

/** A cert's friendly name, or null. */
@OptIn(ExperimentalForeignApi::class)
private fun friendlyNameOf(inCert: CPointer<CERT_CONTEXT>): String? = memScoped {
	val vLen = alloc<UIntVar>()
	if (CertGetCertificateContextProperty(inCert, CERT_FRIENDLY_NAME_PROP_ID.toUInt(), null, vLen.ptr) == 0) return@memScoped null
	val vBuf = allocArray<ByteVar>(vLen.value.toInt())
	if (CertGetCertificateContextProperty(inCert, CERT_FRIENDLY_NAME_PROP_ID.toUInt(), vBuf, vLen.ptr) == 0) return@memScoped null
	vBuf.reinterpret<UShortVar>().toKStringFromUtf16()
}

// ============
//  Chain extension via the OS certificate store
// ============

/** Continue the server's chain by asking the OS to build it from the leaf — this
   pulls intermediates/roots from the Windows store with full info. Falls back to
   a name-only issuer if the OS can't (or the leaf can't be parsed). */
@OptIn(ExperimentalForeignApi::class)
actual fun extendChain(inServerCerts: List<List<Pair<String, String>>>): List<ChainCert>
	{
	val vLeafPem = inServerCerts.firstOrNull()?.let { certFieldOf(it, "Cert") }
		?: return serverChainWithIssuerName(inServerCerts)
	val vOs = runCatching { osChain(vLeafPem, inServerCerts.size) }.getOrNull()
	return if (!vOs.isNullOrEmpty()) vOs else serverChainWithIssuerName(inServerCerts)
	}

/** Build the full chain from the leaf via CertGetCertificateChain (OS store). */
@OptIn(ExperimentalForeignApi::class)
private fun osChain(inLeafPem: String, inServerCount: Int): List<ChainCert> = memScoped {
	val vDer = derFromMaybePem(inLeafPem.encodeToByteArray(), "CERTIFICATE")
	val vLeaf = vDer.usePinned { CertCreateCertificateContext(kEncoding, it.addressOf(0).reinterpret(), vDer.size.convert()) }
		?: return@memScoped emptyList()
	try
		{
		val vPara = alloc<CERT_CHAIN_PARA>()
		vPara.cbSize = sizeOf<CERT_CHAIN_PARA>().convert()
		val vChainPtr = alloc<CPointerVar<CERT_CHAIN_CONTEXT>>()
		if (CertGetCertificateChain(null, vLeaf, null, null, vPara.ptr, 0u, null, vChainPtr.ptr) == 0)
			return@memScoped emptyList()
		val vChain = vChainPtr.value ?: return@memScoped emptyList()
		try
			{
			val vSimple = vChain.pointed.rgpChain?.get(0)?.pointed ?: return@memScoped emptyList()
			val vElems = vSimple.rgpElement ?: return@memScoped emptyList()
			val vResult = ArrayList<ChainCert>()
			for (vI in 0 until vSimple.cElement.toInt())
				{
				val vCtx = vElems[vI]?.pointed?.pCertContext ?: continue
				vResult.add(ChainCert(certFieldsFromContext(vCtx), vI < inServerCount))
				}
			// If the chain didn't reach a self-signed root, end with the name only.
			val vLast = vResult.lastOrNull()
			if (vLast != null)
				{
				val vSub = certFieldOf(vLast.fields, "Subject")
				val vIss = certFieldOf(vLast.fields, "Issuer")
				if (!vIss.isNullOrBlank() && vIss != vSub) vResult.add(ChainCert(listOf("Subject" to vIss), false))
				}
			vResult
			}
		finally { CertFreeCertificateChain(vChain) }
		}
	finally { CertFreeCertificateContext(vLeaf) }
}

/** Subject / Issuer (RDN strings) + the PEM, read from a cert context. */
@OptIn(ExperimentalForeignApi::class)
private fun certFieldsFromContext(inCtx: CPointer<CERT_CONTEXT>): List<Pair<String, String>>
	{
	val vFields = ArrayList<Pair<String, String>>()
	certNameString(inCtx, 0u)?.let { vFields.add("Subject" to it) }
	certNameString(inCtx, CERT_NAME_ISSUER_FLAG.toUInt())?.let { vFields.add("Issuer" to it) }
	certPem(inCtx)?.let { vFields.add("Cert" to it) }
	return vFields
	}

/** A cert's Subject (inFlags=0) or Issuer (CERT_NAME_ISSUER_FLAG) as an X.500 string. */
@OptIn(ExperimentalForeignApi::class)
private fun certNameString(inCtx: CPointer<CERT_CONTEXT>, inFlags: UInt): String? = memScoped {
	val vType = alloc<UIntVar>().apply { value = CERT_X500_NAME_STR.toUInt() }
	val vLen = CertGetNameStringA(inCtx, CERT_NAME_RDN_TYPE.toUInt(), inFlags, vType.ptr, null, 0u)
	if (vLen <= 1u) return@memScoped null
	val vBuf = allocArray<ByteVar>(vLen.toInt())
	CertGetNameStringA(inCtx, CERT_NAME_RDN_TYPE.toUInt(), inFlags, vType.ptr, vBuf, vLen)
	vBuf.toKString()
}

/** The PEM (base64-with-header) of a cert context. */
@OptIn(ExperimentalForeignApi::class)
private fun certPem(inCtx: CPointer<CERT_CONTEXT>): String? = memScoped {
	val vData = inCtx.pointed.pbCertEncoded ?: return@memScoped null
	val vSize = inCtx.pointed.cbCertEncoded
	val vLen = alloc<UIntVar>()
	if (CryptBinaryToStringA(vData, vSize, CRYPT_STRING_BASE64HEADER.toUInt(), null, vLen.ptr) == 0) return@memScoped null
	val vBuf = allocArray<ByteVar>(vLen.value.toInt())
	if (CryptBinaryToStringA(vData, vSize, CRYPT_STRING_BASE64HEADER.toUInt(), vBuf, vLen.ptr) == 0) return@memScoped null
	vBuf.toKString()
}

// ============
//  Small helpers
// ============

/** Decode a PEM block (by label) to DER, or pass bytes through if already DER. */
@OptIn(ExperimentalEncodingApi::class)
private fun derFromMaybePem(inBytes: ByteArray, inLabel: String): ByteArray
	{
	val vText = inBytes.decodeToString()
	if (!vText.contains("-----BEGIN")) return inBytes
	val vBegin = vText.indexOf("-----BEGIN $inLabel-----")
	val vEnd = vText.indexOf("-----END $inLabel-----")
	val vBlock = if (vBegin >= 0 && vEnd >= 0) vText.substring(vBegin, vEnd) else vText
	return Base64.Default.decode(pemBody(vBlock))
	}

/** Extract + DER-decode the first PRIVATE KEY block from PEM text, or pass DER
   bytes through. Null if no key. */
@OptIn(ExperimentalEncodingApi::class)
private fun privateKeyDer(inBytes: ByteArray): ByteArray?
	{
	val vText = inBytes.decodeToString()
	if (!vText.contains("-----BEGIN")) return inBytes   // already DER
	val vMatch = Regex("-----BEGIN [A-Z 0-9]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z 0-9]*PRIVATE KEY-----").find(vText)
		?: return null
	return Base64.Default.decode(pemBody(vMatch.value))
	}

/** The base64 body of a PEM block (drop the BEGIN/END lines and whitespace). */
private fun pemBody(inPem: String): String =
	inPem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("") { it.trim() }

private fun hexToBytes(inHex: String): ByteArray =
	ByteArray(inHex.length / 2) { inHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** Read a null-terminated UTF-16 (wide) string. */
@OptIn(ExperimentalForeignApi::class)
private fun CPointer<UShortVar>.toKStringFromUtf16(): String
	{
	val vSb = StringBuilder()
	var vI = 0
	while (true) { val vC = this[vI].toInt(); if (vC == 0) break; vSb.append(vC.toChar()); vI++ }
	return vSb.toString()
	}

@OptIn(ExperimentalForeignApi::class)
private fun winError(inWhat: String): Nothing =
	throw RuntimeException("$inWhat failed (Windows error 0x${GetLastError().toString(16)})")
