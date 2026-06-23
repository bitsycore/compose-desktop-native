package apidemo

import kotlinx.serialization.Serializable

// ==================
// MARK: Pack model (serialized to / from .json)
// ==================

/* A "pack" is a savable collection of requests plus its environment variables
   — the export/import unit. Variables are referenced as {{name}} in any URL,
   query value, header or body and substituted just before the request is sent
   (see resolveVars in Tools.kt). A fresh pack holds one blank request; the rich
   starter set lives in defaultSession(). */
@Serializable
data class Pack(
    val name: String = "My Pack",
    val requests: List<ApiRequest> = listOf(ApiRequest()),
    val variables: List<KeyVal> = emptyList(),
    val color: Int = 0,   // 1-based index into the pack-colour palette; 0 = none
    val headers: List<KeyVal> = emptyList(),   // pack-level headers, inherited by requests
    val cert: CertConfig? = null,              // pack-level client cert, inherited by requests
    val id: String = "",                       // stable id (used by linked-copy packs)
    val linkedTo: String? = null,              // id of the source pack when this is a linked copy
)

/* A client-certificate (mutual TLS) configuration. Lives on a request today and
   — with the pack tree — on the session / packs too (inherited downward). Maps
   to libcurl's CURLOPT_SSLCERT / SSLCERTTYPE / SSLKEY / SSLKEYTYPE / KEYPASSWD. */
@Serializable
data class CertConfig(
    val certPath: String = "",
    val certFormat: CertFormat = CertFormat.PEM,
    val keyPath: String = "",
    val keyFormat: CertFormat = CertFormat.PEM,
    val certPassword: String = "",
)

/* The starter SESSION loaded on first launch (and on demand from the session
   menu) — the httpbin tour split into themed packs, with the shared base URL /
   creds in the session's global env. Loaded as an unsaved (untitled) session. */
fun defaultSession(): Session = Session(
    activePack = 0,
    globalEnv = listOf(
        KeyVal("baseUrl", "https://httpbin.org"),
        KeyVal("token", "my-secret-token"),
        KeyVal("user", "user"),
        KeyVal("password", "passwd"),
    ),
    globalHeaders = listOf(KeyVal("X-Client", "compose-apidemo")),   // inherited by every request
    packs = listOf(
        SavedPack(pack = Pack(name = "Methods", id = "pk-methods", color = 1,
            headers = listOf(KeyVal("Accept", "application/json")),   // pack-level header, inherited by its requests
            requests = listOf(
            ApiRequest(name = "GET — query params", method = ReqMethod.GET, url = "{{baseUrl}}/get",
                params = listOf(KeyVal("q", "compose"), KeyVal("page", "1"))),
            ApiRequest(name = "POST — JSON", method = ReqMethod.POST, url = "{{baseUrl}}/post",
                bodyType = BodyType.JSON, body = "{\n  \"name\": \"{{user}}\",\n  \"active\": true\n}"),
            ApiRequest(name = "POST — form", method = ReqMethod.POST, url = "{{baseUrl}}/post",
                bodyType = BodyType.FORM, form = listOf(KeyVal("field1", "value1"), KeyVal("field2", "value2"))),
            ApiRequest(name = "PUT — replace", method = ReqMethod.PUT, url = "{{baseUrl}}/put",
                bodyType = BodyType.JSON, body = "{\n  \"id\": 1,\n  \"name\": \"updated\"\n}"),
            ApiRequest(name = "PATCH — partial", method = ReqMethod.PATCH, url = "{{baseUrl}}/patch",
                bodyType = BodyType.JSON, body = "{ \"name\": \"patched\" }"),
            ApiRequest(name = "DELETE", method = ReqMethod.DELETE, url = "{{baseUrl}}/delete"),
            ApiRequest(name = "HEAD", method = ReqMethod.HEAD, url = "{{baseUrl}}/get"),
            ApiRequest(name = "OPTIONS", method = ReqMethod.OPTIONS, url = "{{baseUrl}}/anything"),
            ApiRequest(name = "Anything echo", method = ReqMethod.POST, url = "{{baseUrl}}/anything",
                bodyType = BodyType.JSON, body = "{ \"hello\": \"world\" }"),
        ))),
        SavedPack(pack = Pack(name = "Auth & status", color = 2, requests = listOf(
            ApiRequest(name = "Bearer auth", method = ReqMethod.GET, url = "{{baseUrl}}/bearer",
                headers = listOf(KeyVal("Authorization", "Bearer {{token}}"))),
            ApiRequest(name = "Basic auth", method = ReqMethod.GET, url = "{{baseUrl}}/basic-auth/{{user}}/{{password}}"),
            ApiRequest(name = "Status 200", method = ReqMethod.GET, url = "{{baseUrl}}/status/200"),
            ApiRequest(name = "Status 404", method = ReqMethod.GET, url = "{{baseUrl}}/status/404"),
            ApiRequest(name = "Status 500", method = ReqMethod.GET, url = "{{baseUrl}}/status/500"),
            ApiRequest(name = "Redirect x3", method = ReqMethod.GET, url = "{{baseUrl}}/redirect/3"),
            ApiRequest(name = "Delay 3s (try Cancel)", method = ReqMethod.GET, url = "{{baseUrl}}/delay/3"),
            ApiRequest(name = "Set cookies", method = ReqMethod.GET, url = "{{baseUrl}}/cookies/set",
                params = listOf(KeyVal("name", "value"))),
            ApiRequest(name = "Response headers", method = ReqMethod.GET, url = "{{baseUrl}}/response-headers",
                params = listOf(KeyVal("X-Demo", "compose-native"))),
        ))),
        SavedPack(pack = Pack(name = "Formats", color = 3, requests = listOf(
            ApiRequest(name = "JSON", method = ReqMethod.GET, url = "{{baseUrl}}/json"),
            ApiRequest(name = "XML", method = ReqMethod.GET, url = "{{baseUrl}}/xml"),
            ApiRequest(name = "HTML", method = ReqMethod.GET, url = "{{baseUrl}}/html"),
            ApiRequest(name = "Gzip (decompressed)", method = ReqMethod.GET, url = "{{baseUrl}}/gzip"),
            ApiRequest(name = "UTF-8 text", method = ReqMethod.GET, url = "{{baseUrl}}/encoding/utf8"),
            ApiRequest(name = "Base64 decode", method = ReqMethod.GET, url = "{{baseUrl}}/base64/SFRUUEJJTiBpcyBhd2Vzb21l"),
            ApiRequest(name = "robots.txt", method = ReqMethod.GET, url = "{{baseUrl}}/robots.txt"),
            ApiRequest(name = "Deny", method = ReqMethod.GET, url = "{{baseUrl}}/deny"),
            ApiRequest(name = "UUID", method = ReqMethod.GET, url = "{{baseUrl}}/uuid"),
        ))),
        SavedPack(pack = Pack(name = "Images & files", color = 4, requests = listOf(
            ApiRequest(name = "PNG image", method = ReqMethod.GET, url = "{{baseUrl}}/image/png"),
            ApiRequest(name = "JPEG image", method = ReqMethod.GET, url = "{{baseUrl}}/image/jpeg"),
            ApiRequest(name = "SVG image", method = ReqMethod.GET, url = "{{baseUrl}}/image/svg"),
            ApiRequest(name = "WebP image", method = ReqMethod.GET, url = "{{baseUrl}}/image/webp"),
            ApiRequest(name = "PDF document", method = ReqMethod.GET, url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"),
            ApiRequest(name = "Random bytes (binary)", method = ReqMethod.GET, url = "{{baseUrl}}/bytes/64"),
        ))),
        // Linked copy of "Methods": mirrors its requests read-only but overrides
        // baseUrl to the httpbingo sibling, so the same calls run against another
        // host. Showcases linked packs + per-pack variable override.
        SavedPack(pack = Pack(name = "Methods · httpbingo", color = 6, requests = emptyList(),
            variables = listOf(KeyVal("baseUrl", "https://httpbingo.org")), linkedTo = "pk-methods")),
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
    // Client certificate (mutual TLS). When certPath is set, the request is sent
    // through the libcurl path (CurlMtls.kt) with these mapped to CURLOPT_SSLCERT
    // / SSLCERTTYPE / SSLKEY / SSLKEYTYPE / KEYPASSWD. A PKCS#12 file bundles its
    // own key, so keyPath is left blank for it.
    val certPath: String = "",
    val certFormat: CertFormat = CertFormat.PEM,
    val keyPath: String = "",
    val keyFormat: CertFormat = CertFormat.PEM,
    val certPassword: String = "",
)

/* Whether this request carries a client certificate (and so is sent via the
   libcurl mTLS path rather than the default engine). */
val ApiRequest.hasClientCert: Boolean
    get() = certPath.isNotBlank()

/* This request's cert fields as a CertConfig (certPath may be blank = none). */
fun ApiRequest.certConfig(): CertConfig = CertConfig(certPath, certFormat, keyPath, keyFormat, certPassword)

/* This request with its cert fields replaced by inCert's. */
fun ApiRequest.withCert(inCert: CertConfig): ApiRequest =
    copy(certPath = inCert.certPath, certFormat = inCert.certFormat,
        keyPath = inCert.keyPath, keyFormat = inCert.keyFormat, certPassword = inCert.certPassword)

/* True when this config actually selects a certificate. */
val CertConfig.isSet: Boolean
    get() = certPath.isNotBlank()

/* Certificate / private-key encodings, mapped to libcurl's CURLOPT_SSLCERTTYPE
   / CURLOPT_SSLKEYTYPE string values and to `curl --cert-type` / `--key-type`.
   Runtime support depends on the TLS backend: OpenSSL (macOS/Linux) handles all
   three; Schannel (Windows) effectively supports P12. */
@Serializable
enum class CertFormat(val curlName: String, val label: String) {
    PEM("PEM", "PEM"),
    DER("DER", "DER"),
    PKCS12("P12", "PKCS#12"),
}

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
