package apidemo

import io.ktor.http.HttpStatusCode
import kotlinx.cinterop.*
import libcurl.*
import okio.Buffer
import platform.posix.size_t
import kotlin.time.TimeSource

// ==================
// MARK: Client-certificate (mTLS) sending via libcurl
// ==================
// Ktor's native engines expose no client-certificate API, so a request that
// carries a client certificate is sent here instead — straight through the
// libcurl that ktor-client-curl already bundles (package `libcurl`, embedded
// static archive; Schannel on Windows, OpenSSL on macOS/Linux). This is the
// same TLS stack the default engine uses, so behaviour matches; we just get to
// set CURLOPT_SSLCERT / SSLCERTTYPE / SSLKEY / SSLKEYTYPE / KEYPASSWD.
//
// The transfer is synchronous (curl_easy_perform) and runs on Dispatchers.Default
// from HttpRunner.run(); cancellation is best-effort (an in-flight perform can't
// be interrupted — the result is simply discarded if the caller cancelled).

/** A client certificate readied for libcurl: the CURLOPT_SSLCERT value plus the
   other SSL options to set, and a cleanup to run once the request is done.
   macOS/Linux (OpenSSL) just point curl at the files. Windows (Schannel) imports
   the cert + key into CurrentUser\MY, references it by SHA-1 thumbprint, and the
   cleanup removes it again so the store never accumulates entries. */
class PreparedCert(
	val sslCert: String,            // CURLOPT_SSLCERT (file path, or "CurrentUser\\MY\\<thumbprint>")
	val sslCertType: String?,       // CURLOPT_SSLCERTTYPE (null for a store reference)
	val sslKey: String?,            // CURLOPT_SSLKEY (null when the cert carries its key)
	val sslKeyType: String?,        // CURLOPT_SSLKEYTYPE
	val keyPassword: String?,       // CURLOPT_KEYPASSWD
	val cleanup: () -> Unit,
)

/** Ready the request's client certificate for libcurl (platform-specific).
   Throws with a user-facing message if the cert/key can't be loaded. */
expect fun prepareClientCert(inReq: ApiRequest): PreparedCert

// ChainCert / TlsChain and the sweepTempClientCerts / inspectTlsChain /
// curlSendWithClientCert expects live in commonMain (TlsCommon.kt) — the UI
// consumes them from shared code; the per-OS ClientCert*.kt actuals below the
// native tree actualize sweepTempClientCerts directly.

/** Continue a server-presented chain with its issuer(s). Where possible the
   issuer is resolved from the OS certificate store with full info; otherwise the
   chain ends with a name-only placeholder. Every derived (non-server) cert is
   flagged fromServer = false. Platform-specific (OS store on Windows). */
expect fun extendChain(inServerCerts: List<List<Pair<String, String>>>): List<ChainCert>

/** A CURLINFO_CERTINFO field by name (case-insensitive). */
internal fun certFieldOf(inFields: List<Pair<String, String>>, inName: String): String? =
	inFields.firstOrNull { it.first.equals(inName, ignoreCase = true) }?.second

/** Server certs as ChainCerts; if the last one isn't self-signed, append a
   dotted, name-only issuer so the chain visibly continues. The cross-platform
   fallback when the OS store can't (or isn't able to) resolve the real issuer. */
internal fun serverChainWithIssuerName(inServer: List<List<Pair<String, String>>>): List<ChainCert>
	{
	val vOut = inServer.map { ChainCert(it, true) }.toMutableList()
	val vLast = inServer.lastOrNull()
	if (vLast != null)
		{
		val vSubject = certFieldOf(vLast, "Subject")
		val vIssuer = certFieldOf(vLast, "Issuer")
		if (!vIssuer.isNullOrBlank() && vIssuer != vSubject)
			vOut.add(ChainCert(listOf("Subject" to vIssuer), false))
		}
	return vOut
	}

/** Per-transfer accumulators handed to the C callbacks through a StableRef. */
private class CurlSink
	{
	val body = Buffer()                 // response body bytes (already decompressed by curl)
	val headerRaw = StringBuilder()     // raw response header lines, all responses incl. redirects
	}

/** Body write callback — appends each chunk to the sink's buffer. */
@OptIn(ExperimentalForeignApi::class)
private fun onCurlBody(inBuffer: CPointer<ByteVar>, inSize: size_t, inCount: size_t, inUserdata: COpaquePointer): size_t
	{
	val vSink = inUserdata.asStableRef<CurlSink>().get()
	val vLen = (inSize * inCount).toLong()
	if (vLen > 0) vSink.body.write(inBuffer.readBytes(vLen.toInt()))
	return vLen.convert()
	}

/** Header callback — one parsed header line per call; accumulated for parsing. */
@OptIn(ExperimentalForeignApi::class)
private fun onCurlHeader(inBuffer: CPointer<ByteVar>, inSize: size_t, inCount: size_t, inUserdata: COpaquePointer): size_t
	{
	val vSink = inUserdata.asStableRef<CurlSink>().get()
	val vLen = (inSize * inCount).toLong()
	if (vLen > 0) vSink.headerRaw.append(inBuffer.readBytes(vLen.toInt()).decodeToString())
	return vLen.convert()
	}

/** Sink callback that discards everything (used by the TLS-chain probe so the
   response body/headers don't spill to stdout). */
@OptIn(ExperimentalForeignApi::class)
private fun onCurlDiscard(inBuffer: CPointer<ByteVar>, inSize: size_t, inCount: size_t, inUserdata: COpaquePointer): size_t =
	(inSize * inCount)

/** Open a TLS connection to the request's URL (handshake only — no body) and
   return the server's certificate chain via CURLINFO_CERTINFO. Reuses the
   request's client certificate if it has one (so mTLS endpoints work too).
   inReq is already var-resolved. */
@OptIn(ExperimentalForeignApi::class)
actual fun inspectTlsChain(inReq: ApiRequest): TlsChain
	{
	val vCert = try { if (inReq.hasClientCert) prepareClientCert(inReq) else null }
		catch (e: Throwable) { return TlsChain(emptyList(), e.message ?: "Client certificate error") }
	val vCurl = curl_easy_init() ?: run { vCert?.cleanup(); return TlsChain(emptyList(), "Could not initialize libcurl") }
	try
		{
		curl_easy_setopt(vCurl, CURLOPT_URL, urlWithParams(inReq))
		curl_easy_setopt(vCurl, CURLOPT_NOBODY, 1L)            // just the handshake + headers
		curl_easy_setopt(vCurl, CURLOPT_CERTINFO, 1L)
		curl_easy_setopt(vCurl, CURLOPT_FOLLOWLOCATION, 0L)    // inspect the host as typed
		curl_easy_setopt(vCurl, CURLOPT_WRITEFUNCTION, staticCFunction(::onCurlDiscard))
		curl_easy_setopt(vCurl, CURLOPT_HEADERFUNCTION, staticCFunction(::onCurlDiscard))
		vCert?.let { vC ->
			curl_easy_setopt(vCurl, CURLOPT_SSLCERT, vC.sslCert)
			vC.sslCertType?.let { curl_easy_setopt(vCurl, CURLOPT_SSLCERTTYPE, it) }
			vC.sslKey?.let { curl_easy_setopt(vCurl, CURLOPT_SSLKEY, it) }
			vC.sslKeyType?.let { curl_easy_setopt(vCurl, CURLOPT_SSLKEYTYPE, it) }
			vC.keyPassword?.let { curl_easy_setopt(vCurl, CURLOPT_KEYPASSWD, it) }
		}
		val vCode = curl_easy_perform(vCurl)
		if (vCode != CURLE_OK)
			return TlsChain(emptyList(), curl_easy_strerror(vCode)?.toKString() ?: "libcurl error $vCode")
		val vRaw = readCertInfo(vCurl)
		if (vRaw.isEmpty()) return TlsChain(emptyList(), "No certificate chain reported (not an HTTPS URL, or the TLS backend didn't expose it).")
		return TlsChain(extendChain(vRaw), null)
		}
	finally
		{
		curl_easy_cleanup(vCurl)
		vCert?.cleanup()
		}
	}

/** Read CURLINFO_CERTINFO off a performed handle into per-cert field lists. */
@OptIn(ExperimentalForeignApi::class)
private fun readCertInfo(inCurl: COpaquePointer): List<List<Pair<String, String>>> = memScoped {
	val vPtr = alloc<COpaquePointerVar>()
	if (curl_easy_getinfo(inCurl, CURLINFO_CERTINFO, vPtr.ptr) != CURLE_OK) return@memScoped emptyList()
	val vInfo = vPtr.value?.reinterpret<curl_certinfo>()?.pointed ?: return@memScoped emptyList()
	val vArr = vInfo.certinfo ?: return@memScoped emptyList()
	val vResult = ArrayList<List<Pair<String, String>>>()
	for (vI in 0 until vInfo.num_of_certs)
		{
		val vFields = ArrayList<Pair<String, String>>()
		var vNode = vArr[vI]
		while (vNode != null)
			{
			val vData = vNode.pointed.data?.toKString()
			if (vData != null)
				{
				val vColon = vData.indexOf(':')
				if (vColon > 0) vFields.add(vData.substring(0, vColon) to vData.substring(vColon + 1))
				else vFields.add("" to vData)
				}
			vNode = vNode.pointed.next
			}
		vResult.add(vFields)
		}
	vResult
}

/** Send a client-certificate request through libcurl and adapt the result into
   the same ApiResponse the Ktor path produces. inReq is already var-resolved. */
@OptIn(ExperimentalForeignApi::class)
actual fun curlSendWithClientCert(inReq: ApiRequest): ApiResponse
	{
	val vMark = TimeSource.Monotonic.markNow()
	val vCert = try { prepareClientCert(inReq) }
		catch (e: Throwable) { return errorResponse(e.message ?: "Client certificate error", vMark.elapsedNow().inWholeMilliseconds) }
	val vCurl = curl_easy_init()
		?: run { vCert.cleanup(); return errorResponse("Could not initialize libcurl", vMark.elapsedNow().inWholeMilliseconds) }

	val vSink = CurlSink()
	val vSinkRef = StableRef.create(vSink)
	var vHeaders: CPointer<curl_slist>? = null

	try
		{
		curl_easy_setopt(vCurl, CURLOPT_URL, urlWithParams(inReq))
		curl_easy_setopt(vCurl, CURLOPT_FOLLOWLOCATION, 1L)
		curl_easy_setopt(vCurl, CURLOPT_ACCEPT_ENCODING, "")   // advertise + auto-decompress

		// ============
		//  Method + body
		val vBody = if (inReq.method == ReqMethod.HEAD) null else requestBodyBytes(inReq)
		if (inReq.method == ReqMethod.HEAD)
			curl_easy_setopt(vCurl, CURLOPT_NOBODY, 1L)
		else
			curl_easy_setopt(vCurl, CURLOPT_CUSTOMREQUEST, inReq.method.name)
		if (vBody != null && vBody.bytes.isNotEmpty())
			{
			curl_easy_setopt(vCurl, CURLOPT_POSTFIELDSIZE_LARGE, vBody.bytes.size.toLong())
			vBody.bytes.usePinned { curl_easy_setopt(vCurl, CURLOPT_COPYPOSTFIELDS, it.addressOf(0)) }
			}

		// ============
		//  Headers (+ Content-Type for the body type if the user didn't set one)
		inReq.headers
			.filter { it.enabled && it.key.isNotBlank() }
			.forEach { vHeaders = curl_slist_append(vHeaders, "${it.key}: ${it.value}") }
		val vHasCt = inReq.headers.any { it.enabled && it.key.equals("content-type", ignoreCase = true) }
		if (vBody?.contentType != null && !vHasCt)
			vHeaders = curl_slist_append(vHeaders, "Content-Type: ${vBody.contentType}")
		vHeaders = curl_slist_append(vHeaders, "Expect:")   // disable 100-continue, as Ktor's engine does
		vHeaders?.let { curl_easy_setopt(vCurl, CURLOPT_HTTPHEADER, it) }

		// ============
		//  Response capture callbacks
		curl_easy_setopt(vCurl, CURLOPT_WRITEFUNCTION, staticCFunction(::onCurlBody))
		curl_easy_setopt(vCurl, CURLOPT_WRITEDATA, vSinkRef.asCPointer())
		curl_easy_setopt(vCurl, CURLOPT_HEADERFUNCTION, staticCFunction(::onCurlHeader))
		curl_easy_setopt(vCurl, CURLOPT_HEADERDATA, vSinkRef.asCPointer())

		// ============
		//  Client certificate (prepared per-platform: cert/key files on the
		//  OpenSSL backends, a CurrentUser\MY thumbprint on Windows/Schannel).
		curl_easy_setopt(vCurl, CURLOPT_SSLCERT, vCert.sslCert)
		vCert.sslCertType?.let { curl_easy_setopt(vCurl, CURLOPT_SSLCERTTYPE, it) }
		vCert.sslKey?.let { curl_easy_setopt(vCurl, CURLOPT_SSLKEY, it) }
		vCert.sslKeyType?.let { curl_easy_setopt(vCurl, CURLOPT_SSLKEYTYPE, it) }
		vCert.keyPassword?.let { curl_easy_setopt(vCurl, CURLOPT_KEYPASSWD, it) }

		// ============
		//  Perform
		val vCode = curl_easy_perform(vCurl)
		val vMs = vMark.elapsedNow().inWholeMilliseconds
		if (vCode != CURLE_OK)
			return errorResponse(curl_easy_strerror(vCode)?.toKString() ?: "libcurl error $vCode", vMs)

		val vBytes = vSink.body.readByteArray()
		val vStatus = memScoped { val p = alloc<LongVar>(); curl_easy_getinfo(vCurl, CURLINFO_RESPONSE_CODE, p.ptr); p.value.toInt() }
		val vCt = memScoped { val p = alloc<CPointerVar<ByteVar>>(); curl_easy_getinfo(vCurl, CURLINFO_CONTENT_TYPE, p.ptr); p.value?.toKString() }
		val vVer = memScoped { val p = alloc<LongVar>(); curl_easy_getinfo(vCurl, CURLINFO_HTTP_VERSION, p.ptr); httpVersionName(p.value) }
		val vIsImage = vCt?.startsWith("image/", ignoreCase = true) == true
		val vBinary = !vIsImage && isBinaryBody(vCt, vBytes)

		// Headers we explicitly put on the wire (curl also adds Host / Accept /
		// User-Agent / Content-Length, which it doesn't expose without a debug hook).
		val vSent = buildList {
			inReq.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { add(it.key to it.value) }
			if (vBody?.contentType != null && !vHasCt) add("Content-Type" to vBody.contentType)
		}.sortedBy { it.first.lowercase() }

		return ApiResponse(
			ok = true,
			status = vStatus,
			statusText = runCatching { HttpStatusCode.fromValue(vStatus).description }.getOrDefault(""),
			timeMs = vMs,
			sizeBytes = vBytes.size.toLong(),
			headers = parseRawHeaders(vSink.headerRaw.toString()),
			body = when {
				vIsImage -> ""
				vBinary -> "(${vCt ?: "binary"} · ${vBytes.size} bytes — not shown; use Save as…)"
				else -> vBytes.decodeToString()
			},
			bytes = vBytes,
			contentType = vCt,
			requestHeaders = vSent,
			httpVersion = vVer,
		)
		}
	finally
		{
		vHeaders?.let { curl_slist_free_all(it) }
		curl_easy_cleanup(vCurl)
		vSinkRef.dispose()
		vCert.cleanup()
		}
	}

/** A failed-request ApiResponse with a message (mirrors HttpRunner's catch). */
private fun errorResponse(inMessage: String, inMs: Long): ApiResponse =
	ApiResponse(ok = false, status = 0, statusText = "—", timeMs = inMs, sizeBytes = 0, headers = emptyList(), body = "", error = inMessage)

/** The encoded body for the request's body type, with the Content-Type curl
   should send (null when there's no body). Mirrors HttpRunner.run(). */
private class CurlBody(val contentType: String?, val bytes: ByteArray)

private fun requestBodyBytes(inReq: ApiRequest): CurlBody?
	{
	if (inReq.bodyType == BodyType.NONE) return null
	return when (inReq.bodyType)
		{
		BodyType.TEXT -> CurlBody(inReq.bodyContentType(), inReq.body.encodeToByteArray())
		BodyType.FORM -> CurlBody(inReq.bodyContentType(), formEncode(inReq.form).encodeToByteArray())
		BodyType.FILE -> if (inReq.body.isBlank()) null else CurlBody(inReq.bodyContentType(), readFileBytes(inReq.body))
		BodyType.NONE -> null
		}
	}

/** Parse libcurl's accumulated raw header text into ordered key/value pairs,
   keeping only the final response's block (a new "HTTP/" status line — emitted
   per redirect hop — resets what we've gathered). */
private fun parseRawHeaders(inRaw: String): List<Pair<String, String>>
	{
	var vCurrent = mutableListOf<Pair<String, String>>()
	for (vRawLine in inRaw.split("\r\n", "\n"))
		{
		val vLine = vRawLine.trimEnd()
		if (vLine.isEmpty()) continue
		if (vLine.startsWith("HTTP/", ignoreCase = true)) { vCurrent = mutableListOf(); continue }
		val vColon = vLine.indexOf(':')
		if (vColon <= 0) continue
		vCurrent.add(vLine.substring(0, vColon).trim() to vLine.substring(vColon + 1).trim())
		}
	return vCurrent.sortedBy { it.first.lowercase() }
	}

/** libcurl's CURLINFO_HTTP_VERSION code → a human protocol string. */
@OptIn(ExperimentalForeignApi::class)
private fun httpVersionName(inCode: Long): String = when (inCode)
	{
	CURL_HTTP_VERSION_1_0.toLong() -> "HTTP/1.0"
	CURL_HTTP_VERSION_1_1.toLong() -> "HTTP/1.1"
	CURL_HTTP_VERSION_2_0.toLong() -> "HTTP/2"
	CURL_HTTP_VERSION_3.toLong() -> "HTTP/3"
	else -> "HTTP/1.1"
	}
