package apidemo

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.GzipSource
import okio.buffer
import kotlin.time.TimeSource

// ==================
// MARK: HttpRunner — executes an ApiRequest through Ktor
// ==================

/* Holds one Ktor HttpClient for the app's lifetime. The engine is resolved per
   target from the single engine artifact on the classpath (WinHttp / Darwin /
   Curl). run() is a suspend fun — call it off the UI dispatcher. */
class HttpRunner {

    private val fClient = HttpClient()

    suspend fun run(inReq: ApiRequest): ApiResponse {
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
                if (inReq.method.allowsBody && inReq.bodyType != BodyType.NONE && inReq.body.isNotEmpty()) {
                    when (inReq.bodyType) {
                        BodyType.JSON -> contentType(ContentType.Application.Json)
                        BodyType.TEXT -> contentType(ContentType.Text.Plain)
                        BodyType.FORM -> contentType(ContentType.Application.FormUrlEncoded)
                        BodyType.NONE -> {}
                    }
                    setBody(inReq.body)
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
            ApiResponse(
                ok = true,
                status = vResp.status.value,
                statusText = vResp.status.description,
                timeMs = vMark.elapsedNow().inWholeMilliseconds,
                sizeBytes = vBody.size.toLong(),
                headers = vResp.headers.entries()
                    .flatMap { e -> e.value.map { e.key to it } }
                    .sortedBy { it.first.lowercase() },
                body = if (vIsImage) "" else vBody.decodeToString(),
                bytes = vBody,
                contentType = vContentType,
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

/* True when the bytes are gzip — either the response says so or they carry the
   gzip magic number (1f 8b). */
private fun isGzip(inEncoding: String?, inBytes: ByteArray): Boolean =
    inEncoding?.contains("gzip", ignoreCase = true) == true ||
        (inBytes.size >= 2 && inBytes[0] == 0x1f.toByte() && inBytes[1] == 0x8b.toByte())

/* Inflate a gzip stream to its original bytes via okio's GzipSource. */
private fun gunzip(inBytes: ByteArray): ByteArray {
    val vGz = GzipSource(Buffer().write(inBytes)).buffer()
    val vResult = vGz.readByteArray()
    vGz.close()
    return vResult
}
