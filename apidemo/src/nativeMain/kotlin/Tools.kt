package apidemo

// ==================
// MARK: Variable substitution ({{name}})
// ==================

/* Matches a {{ name }} token. Names may contain letters, digits, _, . and -.
   Surrounding whitespace inside the braces is tolerated. */
private val kVarRegex = Regex("""\{\{\s*([A-Za-z0-9_.\-]+)\s*\}\}""")

/* Replace every {{name}} token in inText with the matching enabled variable's
   value. Unknown / disabled names are left untouched so the user can spot the
   ones that won't resolve. */
fun substituteVars(inText: String, inVars: List<KeyVal>): String {
    if (inText.isEmpty()) return inText
    val vMap = inVars
        .filter { it.enabled && it.key.isNotBlank() }
        .associate { it.key to it.value }
    if (vMap.isEmpty()) return inText
    return kVarRegex.replace(inText) { vMatch -> vMap[vMatch.groupValues[1]] ?: vMatch.value }
}

/* Apply variable substitution across every field that actually gets sent — URL,
   query params, headers and body — returning a fully-resolved request. The
   original (template) request is left untouched. */
fun resolveVars(inReq: ApiRequest, inVars: List<KeyVal>): ApiRequest {
    val vActive = inVars.any { it.enabled && it.key.isNotBlank() }
    if (!vActive) return inReq
    return inReq.copy(
        url = substituteVars(inReq.url, inVars),
        params = inReq.params.map { it.copy(key = substituteVars(it.key, inVars), value = substituteVars(it.value, inVars)) },
        headers = inReq.headers.map { it.copy(key = substituteVars(it.key, inVars), value = substituteVars(it.value, inVars)) },
        body = substituteVars(inReq.body, inVars),
        form = inReq.form.map { it.copy(key = substituteVars(it.key, inVars), value = substituteVars(it.value, inVars)) },
    )
}

/* The distinct {{names}} referenced anywhere in the request that have no enabled
   variable to fill them — surfaced as a warning in the editor. */
fun unresolvedVars(inReq: ApiRequest, inVars: List<KeyVal>): List<String> {
    val vDefined = inVars
        .filter { it.enabled && it.key.isNotBlank() }
        .map { it.key }
        .toSet()
    val vText = buildString {
        append(inReq.url).append('\n')
        inReq.params.forEach { append(it.key).append('\n').append(it.value).append('\n') }
        inReq.headers.forEach { append(it.key).append('\n').append(it.value).append('\n') }
        if (inReq.method.allowsBody) {
            when (inReq.bodyType) {
                BodyType.FORM -> inReq.form.forEach { append(it.key).append('\n').append(it.value).append('\n') }
                else -> append(inReq.body)
            }
        }
    }
    return kVarRegex.findAll(vText)
        .map { it.groupValues[1] }
        .filter { it !in vDefined }
        .distinct()
        .toList()
}

// ==================
// MARK: Copy as cURL
// ==================

/* Render inReq as a runnable, multi-line curl command (pass an already-resolved
   request so the output has real values, not {{tokens}}). Enabled query params
   are folded into the URL; a Content-Type header is added for the body type
   unless the request already sets one. */
fun toCurl(inReq: ApiRequest): String {
    val vSb = StringBuilder()
    vSb.append("curl -X ").append(inReq.method.name)
    vSb.append(" \\\n  ").append(shellQuote(urlWithParams(inReq)))

    inReq.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .forEach { vSb.append(" \\\n  -H ").append(shellQuote("${it.key}: ${it.value}")) }

    if (inReq.method.allowsBody && inReq.bodyType != BodyType.NONE) {
        val vContentType = when (inReq.bodyType) {
            BodyType.JSON -> "application/json"
            BodyType.TEXT -> "text/plain"
            BodyType.FORM -> "application/x-www-form-urlencoded"
            BodyType.FILE -> "application/octet-stream"
            BodyType.NONE -> null
        }
        val vHasCt = inReq.headers.any { it.enabled && it.key.equals("content-type", ignoreCase = true) }
        if (vContentType != null && !vHasCt) {
            vSb.append(" \\\n  -H ").append(shellQuote("Content-Type: $vContentType"))
        }
        when (inReq.bodyType) {
            BodyType.FORM -> if (inReq.form.any { it.enabled && it.key.isNotBlank() }) vSb.append(" \\\n  --data ").append(shellQuote(formEncode(inReq.form)))
            BodyType.FILE -> if (inReq.body.isNotBlank()) vSb.append(" \\\n  --data-binary ").append(shellQuote("@${inReq.body}"))
            else -> if (inReq.body.isNotEmpty()) vSb.append(" \\\n  --data ").append(shellQuote(inReq.body))
        }
    }
    return vSb.toString()
}

/* URL-encode enabled form fields into an application/x-www-form-urlencoded body. */
fun formEncode(inForm: List<KeyVal>): String =
    inForm.filter { it.enabled && it.key.isNotBlank() }
        .joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }

/* The request URL with its enabled query params appended (percent-encoded). */
internal fun urlWithParams(inReq: ApiRequest): String {
    val vTrimmed = inReq.url.trim()
    val vEnabled = inReq.params.filter { it.enabled && it.key.isNotBlank() }
    if (vEnabled.isEmpty()) return vTrimmed
    val vSep = if (vTrimmed.contains('?')) "&" else "?"
    val vQuery = vEnabled.joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }
    return vTrimmed + vSep + vQuery
}

/* Single-quote a string for a POSIX shell, escaping any embedded quotes. */
private fun shellQuote(inS: String): String = "'" + inS.replace("'", "'\\''") + "'"

/* Minimal RFC-3986 percent-encoding for query keys / values. */
private fun urlEncode(inS: String): String = buildString {
    for (vByte in inS.encodeToByteArray()) {
        val vCode = vByte.toInt() and 0xFF
        val vChar = vCode.toChar()
        // Only unreserved ASCII passes through; every byte >= 0x80 (multibyte
        // UTF-8) is always percent-encoded.
        if (vCode < 0x80 && (vChar.isLetterOrDigit() || vChar in "-_.~")) {
            append(vChar)
        } else {
            append('%').append(vCode.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
