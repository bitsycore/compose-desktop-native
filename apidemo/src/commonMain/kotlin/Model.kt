package apidemo

import kotlinx.serialization.Serializable

// ==================
// MARK: Pack model (serialized to / from .json)
// ==================

/** A "pack" is a savable collection of requests plus its environment variables
— the export/import unit. Variables are referenced as {{name}} in any URL,
query value, header or body and substituted just before the request is sent
(see resolveVars in Tools.kt). Packs may be empty; the rich starter set lives
in defaultSession(). */
@Serializable
data class Pack(
    val name: String = "My Pack",
    val requests: List<ApiRequest> = emptyList(),
    val variables: List<KeyVal> = emptyList(),
    val color: Int = 0,   // 1-based index into the pack-colour palette; 0 = none
    val headers: List<KeyVal> = emptyList(),   // pack-level headers, inherited by requests
    val params: List<KeyVal> = emptyList(),    // pack-level query params, inherited by requests
    val cert: CertConfig? = null,              // pack-level client cert, inherited by requests
    val id: String = "",                       // stable id (used by linked-copy packs)
    val linkedTo: String? = null,              // id of the source pack when this is a linked copy
    val isRoot: Boolean = false,               // the hidden session-root "pack" holding loose requests
    val subPacks: List<Pack> = emptyList(),    // nested packs (a pack tree)
)

/** A client-certificate (mutual TLS) configuration. Lives on a request today and
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

/** The starter SESSION loaded on first launch (and on demand from the session
menu) — a guided tour of every feature against httpbin.org: the inheritance
ladder (session → pack → sub-pack → request, for variables / query params /
headers / client cert, innermost wins), loose root requests, a nested sub-pack,
per-request overrides, a client-cert pack, and a linked-copy pack. Each /get or
/anything endpoint echoes the args + headers it received, so the merged,
inherited result is visible in the response. Loaded as an unsaved session. */
fun defaultSession(): Session = Session(
    activePack = 0,
    // ── Session level — the base of every inheritance ladder ──
    globalEnv = listOf(
        KeyVal("baseUrl", "https://httpbin.org"),
        KeyVal("token", "session-token-abc"),
        KeyVal("user", "session-user"),
        KeyVal("password", "passwd"),
        KeyVal("apiVer", "v1"),                                       // overridden by Methods (v2) then Nested (v3)
    ),
    globalHeaders = listOf(KeyVal("X-Client", "compose-apidemo")),    // every request sends it (echoed by /headers)
    globalParams = listOf(
        KeyVal(
            "trace",
            "session"
        )
    ),                // every request gets ?trace=session (echoed in /get args)
    // No session-level cert: it would route every request through mTLS and fail
    // without a real cert file. Client certs are shown on the "Secure (mTLS)" pack.
    // ── Loose requests at the session root (in no pack — inherit session only) ──
    root = Pack(
        isRoot = true, name = "", requests = listOf(
            ApiRequest(name = "Ping (loose)", method = ReqMethod.GET, url = "{{baseUrl}}/get"),
            ApiRequest(name = "Headers echo (loose)", method = ReqMethod.GET, url = "{{baseUrl}}/headers"),
        )
    ),
    packs = listOf(
        // ── Methods — pack header/param/var inherited by its requests, plus a
        //    nested sub-pack and per-request overrides of each kind. ──
        SavedPack(
            pack = Pack(
                name = "Methods", id = "pk-methods", color = 1,
                headers = listOf(KeyVal("Accept", "application/json")),   // pack header, inherited by its requests
                params = listOf(
                    KeyVal(
                        "source",
                        "methods"
                    )
                ),             // pack query param, inherited (echoed in /get args)
                variables = listOf(
                    KeyVal(
                        "apiVer",
                        "v2"
                    )
                ),               // overrides the session's apiVer for this pack
                requests = listOf(
                    ApiRequest(
                        name = "GET — echoes inherited", method = ReqMethod.GET, url = "{{baseUrl}}/get",
                        params = listOf(KeyVal("q", "compose"))
                    ),         // own q + inherited trace=session + source=methods
                    ApiRequest(
                        name = "GET — overrides query param", method = ReqMethod.GET, url = "{{baseUrl}}/get",
                        params = listOf(KeyVal("trace", "request"))
                    ),     // request's trace wins over the session's
                    ApiRequest(
                        name = "POST — overrides header",
                        method = ReqMethod.POST,
                        url = "{{baseUrl}}/post",
                        headers = listOf(KeyVal("Accept", "text/plain")), // request's Accept wins over the pack's
                        bodyType = BodyType.TEXT,
                        bodyFormat = BodyFormat.JSON,
                        body = "{\n  \"user\": \"{{user}}\",\n  \"api\": \"{{apiVer}}\"\n}"
                    ),
                    ApiRequest(
                        name = "POST — overrides {{user}} var", method = ReqMethod.POST, url = "{{baseUrl}}/anything",
                        variables = listOf(
                            KeyVal(
                                "user",
                                "request-user"
                            )
                        ),   // request var beats session/pack — see body echo
                        bodyType = BodyType.TEXT, bodyFormat = BodyFormat.JSON, body = "{ \"who\": \"{{user}}\" }"
                    ),
                    ApiRequest(
                        name = "PUT — replace",
                        method = ReqMethod.PUT,
                        url = "{{baseUrl}}/put",
                        bodyType = BodyType.TEXT,
                        bodyFormat = BodyFormat.JSON,
                        body = "{ \"id\": 1, \"name\": \"updated\" }"
                    ),
                    ApiRequest(name = "DELETE", method = ReqMethod.DELETE, url = "{{baseUrl}}/delete"),
                    ApiRequest(
                        name = "Anything echo", method = ReqMethod.POST, url = "{{baseUrl}}/anything",
                        bodyType = BodyType.TEXT, bodyFormat = BodyFormat.JSON, body = "{ \"hello\": \"world\" }"
                    ),
                ),
                // Nested sub-pack — inherits Session → Methods → here (apiVer becomes v3,
                // plus its own X-Nested header; trace / source / Accept still flow down).
                subPacks = listOf(
                    Pack(
                        name = "Nested", color = 4,
                        variables = listOf(KeyVal("apiVer", "v3")),
                        headers = listOf(KeyVal("X-Nested", "true")),
                        requests = listOf(
                            ApiRequest(
                                name = "GET — deep inheritance",
                                method = ReqMethod.GET,
                                url = "{{baseUrl}}/anything/{{apiVer}}"
                            ),
                        )
                    ),
                )
            )
        ),
        // ── Auth & status — uses the inherited {{token}} / {{user}} / {{password}} ──
        SavedPack(
            pack = Pack(
                name = "Auth & status", color = 2, requests = listOf(
                    ApiRequest(
                        name = "Bearer auth", method = ReqMethod.GET, url = "{{baseUrl}}/bearer",
                        headers = listOf(KeyVal("Authorization", "Bearer {{token}}"))
                    ),
                    ApiRequest(
                        name = "Basic auth",
                        method = ReqMethod.GET,
                        url = "{{baseUrl}}/basic-auth/{{user}}/{{password}}"
                    ),
                    ApiRequest(name = "Status 200", method = ReqMethod.GET, url = "{{baseUrl}}/status/200"),
                    ApiRequest(name = "Status 500", method = ReqMethod.GET, url = "{{baseUrl}}/status/500"),
                    ApiRequest(name = "Redirect x3", method = ReqMethod.GET, url = "{{baseUrl}}/redirect/3"),
                    ApiRequest(name = "Delay 3s (try Cancel)", method = ReqMethod.GET, url = "{{baseUrl}}/delay/3"),
                )
            )
        ),
        // ── Formats — response viewer (JSON / XML / HTML / image) ──
        SavedPack(
            pack = Pack(
                name = "Formats", color = 3, requests = listOf(
                    ApiRequest(name = "JSON", method = ReqMethod.GET, url = "{{baseUrl}}/json"),
                    ApiRequest(name = "XML", method = ReqMethod.GET, url = "{{baseUrl}}/xml"),
                    ApiRequest(name = "HTML", method = ReqMethod.GET, url = "{{baseUrl}}/html"),
                    ApiRequest(name = "UTF-8 text", method = ReqMethod.GET, url = "{{baseUrl}}/encoding/utf8"),
                    ApiRequest(name = "PNG image", method = ReqMethod.GET, url = "{{baseUrl}}/image/png"),
                    ApiRequest(name = "SVG image", method = ReqMethod.GET, url = "{{baseUrl}}/image/svg"),
                )
            )
        ),
        // ── Secure (mTLS) — a PACK-LEVEL client cert, inherited by its requests.
        //    Open a request's Cert tab to see the inherited cert (source pill +
        //    Override). Set real cert paths to actually send; the example paths
        //    won't load. The second request overrides with its own cert. ──
        SavedPack(
            pack = Pack(
                name = "Secure (mTLS)", color = 5,
                cert = CertConfig(
                    certPath = "/path/to/client.p12",
                    certFormat = CertFormat.PKCS12,
                    certPassword = "changeit"
                ),
                requests = listOf(
                    ApiRequest(name = "Inherits the pack cert", method = ReqMethod.GET, url = "{{baseUrl}}/get"),
                    ApiRequest(
                        name = "Overrides with own cert",
                        method = ReqMethod.GET,
                        url = "{{baseUrl}}/get",
                        certPath = "/path/to/other-client.pem",
                        certFormat = CertFormat.PEM,
                        keyPath = "/path/to/other-key.pem"
                    ),
                )
            )
        ),
        // ── Linked copy of "Methods": a read-only mirror of its requests, but with
        //    its own baseUrl var → the same calls run against the httpbingo sibling.
        //    Showcases linked packs + per-pack variable override. ──
        SavedPack(
            pack = Pack(
                name = "Methods · httpbingo", color = 6, requests = emptyList(),
                variables = listOf(KeyVal("baseUrl", "https://httpbingo.org")), linkedTo = "pk-methods"
            )
        ),
    ),
)

@Serializable
data class ApiRequest(
    val name: String = "New request",
    val method: ReqMethod = ReqMethod.GET,
    val url: String = "https://",
    val params: List<KeyVal> = emptyList(),
    val variables: List<KeyVal> = emptyList(),   // request-level vars ({{name}}); override inherited ones
    val headers: List<KeyVal> = emptyList(),
    val bodyType: BodyType = BodyType.NONE,
    val body: String = "",                       // TEXT content, or the file path when bodyType == FILE
    val bodyFormat: BodyFormat = BodyFormat.RAW, // for a TEXT body: syntax-highlight type + sent Content-Type
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

/** Whether this request carries a client certificate (and so is sent via the
libcurl mTLS path rather than the default engine). */
val ApiRequest.hasClientCert: Boolean
    get() = certPath.isNotBlank()

/** This request's cert fields as a CertConfig (certPath may be blank = none). */
fun ApiRequest.certConfig(): CertConfig = CertConfig(certPath, certFormat, keyPath, keyFormat, certPassword)

/** This request with its cert fields replaced by inCert's. */
fun ApiRequest.withCert(inCert: CertConfig): ApiRequest =
    copy(
        certPath = inCert.certPath, certFormat = inCert.certFormat,
        keyPath = inCert.keyPath, keyFormat = inCert.keyFormat, certPassword = inCert.certPassword
    )

/** True when this config actually selects a certificate. */
val CertConfig.isSet: Boolean
    get() = certPath.isNotBlank()

/** Certificate / private-key encodings, mapped to libcurl's CURLOPT_SSLCERTTYPE
/ CURLOPT_SSLKEYTYPE string values and to `curl --cert-type` / `--key-type`.
Runtime support depends on the TLS backend: OpenSSL (macOS/Linux) handles all
three; Schannel (Windows) effectively supports P12. */
@Serializable
enum class CertFormat(val curlName: String, val label: String) {
    PEM("PEM", "PEM"),
    DER("DER", "DER"),
    PKCS12("P12", "PKCS#12"),
}

/** A toggleable key/value row, used for both query params and headers. */
@Serializable
data class KeyVal(val key: String = "", val value: String = "", val enabled: Boolean = true)

@Serializable
enum class ReqMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }

/** HTTP doesn't forbid a body on GET / HEAD / OPTIONS — RFC 7230 §3.3
leaves it as "SHOULD NOT" / server discretion. Many APIs use a body
on GET (Elasticsearch search, GraphQL queries) so we don't gate it
here; the UI just sets bodyType=NONE by default for those methods. */
val ReqMethod.allowsBody: Boolean
    get() = true

@Serializable
enum class BodyType { NONE, TEXT, FORM, FILE }

/** Title-case label for the body-type picker. */
val BodyType.label: String
    get() = when (this) {
        BodyType.NONE -> "None"
        BodyType.TEXT -> "Text"
        BodyType.FORM -> "Form"
        BodyType.FILE -> "File"
    }

/** The Content-Type the request body implies (null when there's no body). For a
TEXT body it comes from the chosen format (Text→text/plain, JSON→application/
json, …); FORM / FILE are fixed. Sent unless the request already sets one. */
fun ApiRequest.bodyContentType(): String? = when (bodyType) {
    BodyType.NONE -> null
    BodyType.TEXT -> bodyFormat.contentType
    BodyType.FORM -> "application/x-www-form-urlencoded"
    BodyType.FILE -> "application/octet-stream"
}

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

/** True when the response carries an image payload (rendered, not shown as text). */
val ApiResponse.isImage: Boolean
    get() = contentType?.startsWith("image/", ignoreCase = true) == true
