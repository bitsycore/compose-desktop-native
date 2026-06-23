package apidemo

import kotlinx.serialization.Serializable

// ==================
// MARK: Pack model (serialized to / from .json)
// ==================

/* A "pack" is a savable collection of requests plus its environment variables
   — the export/import unit. Variables are referenced as {{name}} in any URL,
   query value, header or body and substituted just before the request is sent
   (see resolveVars in Tools.kt). A fresh pack holds one blank request; the rich
   starter set lives in defaultPack(). */
@Serializable
data class Pack(
    val name: String = "My Pack",
    val requests: List<ApiRequest> = listOf(ApiRequest()),
    val variables: List<KeyVal> = emptyList(),
    val color: Int = 0,   // 1-based index into the pack-colour palette; 0 = none
)

/* The starter pack loaded on first launch (and on demand from the options menu)
   — a tour of httpbin.org covering every method, auth styles, status codes,
   a long delay (good for testing Cancel), redirects and response formats. */
fun defaultPack(): Pack = Pack(
    name = "httpbin",
    variables = listOf(
        KeyVal("baseUrl", "https://httpbin.org"),
        KeyVal("token", "my-secret-token"),
        KeyVal("user", "user"),
        KeyVal("password", "passwd"),
    ),
    requests = listOf(
        ApiRequest(name = "GET — query params", method = ReqMethod.GET, url = "{{baseUrl}}/get",
            params = listOf(KeyVal("q", "compose"), KeyVal("page", "1"))),
        ApiRequest(name = "POST — JSON body", method = ReqMethod.POST, url = "{{baseUrl}}/post",
            bodyType = BodyType.JSON, body = "{\n  \"name\": \"{{user}}\",\n  \"active\": true\n}"),
        ApiRequest(name = "POST — form", method = ReqMethod.POST, url = "{{baseUrl}}/post",
            bodyType = BodyType.FORM, body = "field1=value1&field2=value2"),
        ApiRequest(name = "PUT — replace", method = ReqMethod.PUT, url = "{{baseUrl}}/put",
            bodyType = BodyType.JSON, body = "{\n  \"id\": 1,\n  \"name\": \"updated\"\n}"),
        ApiRequest(name = "PATCH — partial", method = ReqMethod.PATCH, url = "{{baseUrl}}/patch",
            bodyType = BodyType.JSON, body = "{ \"name\": \"patched\" }"),
        ApiRequest(name = "DELETE", method = ReqMethod.DELETE, url = "{{baseUrl}}/delete"),
        ApiRequest(name = "Custom headers", method = ReqMethod.GET, url = "{{baseUrl}}/headers",
            headers = listOf(KeyVal("X-Demo", "compose-native"), KeyVal("Accept", "application/json"))),
        ApiRequest(name = "Bearer auth", method = ReqMethod.GET, url = "{{baseUrl}}/bearer",
            headers = listOf(KeyVal("Authorization", "Bearer {{token}}"))),
        ApiRequest(name = "Basic auth", method = ReqMethod.GET, url = "{{baseUrl}}/basic-auth/{{user}}/{{password}}"),
        ApiRequest(name = "Status 418", method = ReqMethod.GET, url = "{{baseUrl}}/status/418"),
        ApiRequest(name = "Delay 5s (try Cancel)", method = ReqMethod.GET, url = "{{baseUrl}}/delay/5"),
        ApiRequest(name = "Redirect x3", method = ReqMethod.GET, url = "{{baseUrl}}/redirect/3"),
        ApiRequest(name = "JSON response", method = ReqMethod.GET, url = "{{baseUrl}}/json"),
        ApiRequest(name = "XML response", method = ReqMethod.GET, url = "{{baseUrl}}/xml"),
        ApiRequest(name = "HTML response", method = ReqMethod.GET, url = "{{baseUrl}}/html"),
        ApiRequest(name = "Gzip", method = ReqMethod.GET, url = "{{baseUrl}}/gzip"),
        ApiRequest(name = "UUID", method = ReqMethod.GET, url = "{{baseUrl}}/uuid"),
        ApiRequest(name = "Image (PNG)", method = ReqMethod.GET, url = "{{baseUrl}}/image/png"),
        ApiRequest(name = "Anything echo", method = ReqMethod.POST, url = "{{baseUrl}}/anything",
            bodyType = BodyType.JSON, body = "{ \"hello\": \"world\" }"),
    ),
)

@Serializable
data class ApiRequest(
    val name: String = "New request",
    val method: ReqMethod = ReqMethod.GET,
    val url: String = "https://",
    val params: List<KeyVal> = emptyList(),
    val headers: List<KeyVal> = emptyList(),
    val bodyType: BodyType = BodyType.NONE,
    val body: String = "",                       // JSON / TEXT content, or the file path when bodyType == FILE
    val form: List<KeyVal> = emptyList(),        // key/value fields when bodyType == FORM
)

/* A toggleable key/value row, used for both query params and headers. */
@Serializable
data class KeyVal(val key: String = "", val value: String = "", val enabled: Boolean = true)

@Serializable
enum class ReqMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }

/* HTTP doesn't forbid a body on GET / HEAD / OPTIONS — RFC 7230 §3.3
   leaves it as "SHOULD NOT" / server discretion. Many APIs use a body
   on GET (Elasticsearch search, GraphQL queries) so we don't gate it
   here; the UI just sets bodyType=NONE by default for those methods. */
val ReqMethod.allowsBody: Boolean
    get() = true

@Serializable
enum class BodyType { NONE, JSON, TEXT, FORM, FILE }

// ==================
// MARK: Response (runtime only — not part of a pack)
// ==================

class ApiResponse(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val timeMs: Long,
    val sizeBytes: Long,
    val headers: List<Pair<String, String>>,
    val body: String,
    val bytes: ByteArray = ByteArray(0),
    val contentType: String? = null,
    val requestHeaders: List<Pair<String, String>> = emptyList(),  // headers actually sent
    val httpVersion: String = "HTTP/1.1",  // from Ktor's response.version, e.g. "HTTP/1.1" or "HTTP/2"
    val error: String? = null,
)

/* True when the response carries an image payload (rendered, not shown as text). */
val ApiResponse.isImage: Boolean
    get() = contentType?.startsWith("image/", ignoreCase = true) == true
