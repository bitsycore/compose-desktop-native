package apidemo

import io.ktor.client.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

// ==================
// MARK: HttpRunner — executes an ApiRequest through Ktor
// ==================

/* Holds one Ktor HttpClient for the app's lifetime. The engine is resolved per
   target from the single engine artifact on the classpath (WinHttp / Darwin /
   Curl). run() is a suspend fun — call it off the UI dispatcher. */
class HttpRunner {

    private val fClient = HttpClient {
        // Advertise Accept-Encoding and transparently decompress gzip/deflate
        // responses (e.g. httpbin /gzip), so bodies aren't read as garbage.
        install(ContentEncoding) {
            gzip()
            deflate()
        }
    }

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
            // Decide by Content-Type before reading the (single-shot) body:
            //  - text → bodyAsText(), which runs through the receive pipeline so
            //    ContentEncoding actually decompresses gzip/deflate and the charset
            //    is honoured (readRawBytes() reads the raw, still-encoded channel).
            //  - image → readRawBytes(), the real binary payload to render.
            val vContentType = vResp.contentType()?.toString()
            val vIsImage = vContentType?.startsWith("image/", ignoreCase = true) == true
            val vBytes = if (vIsImage) vResp.readRawBytes() else ByteArray(0)
            val vText = if (vIsImage) "" else vResp.bodyAsText()
            ApiResponse(
                ok = true,
                status = vResp.status.value,
                statusText = vResp.status.description,
                timeMs = vMark.elapsedNow().inWholeMilliseconds,
                sizeBytes = (if (vIsImage) vBytes.size else vText.length).toLong(),
                headers = vResp.headers.entries()
                    .flatMap { e -> e.value.map { e.key to it } }
                    .sortedBy { it.first.lowercase() },
                body = vText,
                bytes = vBytes,
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
