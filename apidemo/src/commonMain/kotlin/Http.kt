package apidemo

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.GzipSource
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.time.TimeSource

// ==================
// MARK: HttpRunner — executes an ApiRequest through Ktor
// ==================

/** Holds one Ktor HttpClient for the app's lifetime. One engine on every desktop
target: Ktor's Curl engine (bundled libcurl — Schannel on Windows, OpenSSL on
macOS/Linux). Same TLS stack as the client-cert path in CurlMtls.kt.
run() is a suspend fun — call it off the UI dispatcher. */
class HttpRunner {

    private val fClient = createApiHttpClient()

    init {
        // Clear any temporary client certs a prior crash left in the Windows
        // store (no-op on other platforms).
        sweepTempClientCerts()
    }

    suspend fun run(inReq: ApiRequest): ApiResponse {
        // Client-certificate requests bypass Ktor (no engine exposes a cert API)
        // and go straight through libcurl — same bundled TLS stack.
        if (inReq.hasClientCert) return curlSendWithClientCert(inReq)
        val vMark = TimeSource.Monotonic.markNow()
        return try {
            val vResp: HttpResponse = fClient.request(inReq.url.trim()) {
                method = HttpMethod.parse(inReq.method.name)
                inReq.params
                    .filter { it.enabled && it.key.isNotBlank() }
                    .forEach { url.parameters.append(it.key, it.value) }
                inReq.headers
                    .filter { it.enabled && it.key.isNotBlank() }
                    .forEach { header(it.key, it.value) }
                if (inReq.method.allowsBody && inReq.bodyType != BodyType.NONE) {
                    // Content-Type comes from the body type / text format, unless the
                    // request already carries an explicit Content-Type header.
                    val vCt = inReq.bodyContentType()
                    val vHasCt = inReq.headers.any { it.enabled && it.key.equals("content-type", ignoreCase = true) }
                    if (vCt != null && !vHasCt) contentType(ContentType.parse(vCt))
                    when (inReq.bodyType) {
                        BodyType.TEXT -> if (inReq.body.isNotEmpty()) setBody(inReq.body)
                        BodyType.FORM -> setBody(formEncode(inReq.form))
                        BodyType.FILE -> if (inReq.body.isNotBlank()) setBody(readFileBytes(inReq.body))
                        BodyType.NONE -> {}
                    }
                }
            }
            // Read the raw payload, then gunzip ourselves if needed. Ktor's
            // ContentEncoding plugin throws on a valid gzip stream on Kotlin/Native,
            // so we don't install it; instead we detect gzip from the response's
            // Content-Encoding header (or the 1f 8b magic) and inflate with okio.
            val vRaw = vResp.readRawBytes()
            val vEncoding = vResp.headers[HttpHeaders.ContentEncoding]
            val vBody = if (isGzip(vEncoding, vRaw)) gunzip(vRaw) else vRaw

            val vContentType = vResp.contentType()?.toString()
            val vIsImage = vContentType?.startsWith("image/", ignoreCase = true) == true
            // Non-image binary (PDF, octet-stream, audio…) would decode to garbage,
            // so show a short note instead and let the user Save the raw bytes.
            val vBinary = !vIsImage && isBinaryBody(vContentType, vBody)
            ApiResponse(
                ok = true,
                status = vResp.status.value,
                statusText = vResp.status.description,
                timeMs = vMark.elapsedNow().inWholeMilliseconds,
                sizeBytes = vBody.size.toLong(),
                headers = vResp.headers.entries()
                    .flatMap { e -> e.value.map { e.key to it } }
                    .sortedBy { it.first.lowercase() },
                body = when {
                    vIsImage -> ""
                    vBinary -> "(${vContentType ?: "binary"} · ${vBody.size} bytes — not shown; use Save as…)"
                    else -> vBody.decodeToString()
                },
                bytes = vBody,
                contentType = vContentType,
                // The headers Ktor actually put on the wire (incl. its auto-added
                // Accept / Accept-Encoding / User-Agent / Host), so the Request tab
                // shows what was sent rather than a guess.
                requestHeaders = vResp.call.request.headers.entries()
                    .flatMap { e -> e.value.map { e.key to it } }
                    .sortedBy { it.first.lowercase() },
                // HttpProtocolVersion.toString() gives "HTTP/1.1" (or
                // "HTTP/2"); .name on its own would just be "HTTP".
                httpVersion = vResp.version.toString(),
            )
        } catch (e: CancellationException) {
            // The caller cancelled (Cancel button) — let it propagate so the
            // request is dropped rather than reported as a failed response.
            throw e
        } catch (e: Throwable) {
            ApiResponse(
                ok = false,
                status = 0,
                statusText = "—",
                timeMs = vMark.elapsedNow().inWholeMilliseconds,
                sizeBytes = 0,
                headers = emptyList(),
                body = "",
                error = e.message ?: e.toString(),
            )
        }
    }

    fun close() = fClient.close()
}

/** Heuristic for a non-text body: textual content types (text, json, xml, html,
javascript, csv, form) are shown as text; everything else, or bytes with
embedded NULs, is treated as binary. */
internal fun isBinaryBody(inContentType: String?, inBytes: ByteArray): Boolean {
    val vCt = inContentType?.lowercase() ?: ""
    val vTextual = vCt.startsWith("text/") ||
            vCt.contains("json") || vCt.contains("xml") || vCt.contains("html") ||
            vCt.contains("javascript") || vCt.contains("csv") || vCt.contains("x-www-form-urlencoded")
    if (vTextual) return false
    if (vCt.isNotEmpty()) return true                       // a non-textual content type → binary
    return inBytes.take(1024).any { it.toInt() == 0 }       // no type: sniff for NUL bytes
}

/** True when the bytes are gzip — either the response says so or they carry the
gzip magic number (1f 8b). */
private fun isGzip(inEncoding: String?, inBytes: ByteArray): Boolean =
    inEncoding?.contains("gzip", ignoreCase = true) == true ||
            (inBytes.size >= 2 && inBytes[0] == 0x1f.toByte() && inBytes[1] == 0x8b.toByte())

/** Read a file's raw bytes (used for a FILE request body). */
internal fun readFileBytes(inPath: String): ByteArray =
    systemFileSystem.read(inPath.trim().toPath()) { readByteArray() }

/** Inflate a gzip stream to its original bytes via okio's GzipSource. */
private fun gunzip(inBytes: ByteArray): ByteArray {
    val vGz = GzipSource(Buffer().write(inBytes)).buffer()
    val vResult = vGz.readByteArray()
    vGz.close()
    return vResult
}
