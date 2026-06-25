package apidemo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// ==================
// MARK: Body auto-format (magic-wand button)
// ==================

// Lenient + pretty, tab-indented (one '\t' per level) so formatted output
// respects the editor's "tab size" (TextLayoutConfig.tabWidth), exactly like
// typed tabs. Lenient so slightly-off JSON (single quotes, trailing commas)
// still reformats instead of failing.
private val fPrettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "\t"
    isLenient = true
}

// Pretty-prints the body for its format. JSON is reparsed and re-emitted
// tab-indented; XML is re-indented by tag depth; every other format (and any
// parse error) returns the text unchanged so the user never loses content.
fun formatBody(inText: String, inFormat: BodyFormat): String =
    when (inFormat) {
        BodyFormat.JSON -> runCatching {
            fPrettyJson.encodeToString(JsonElement.serializer(), fPrettyJson.parseToJsonElement(inText))
        }.getOrDefault(inText)
        BodyFormat.XML -> runCatching { formatXml(inText) }.getOrDefault(inText)
        else -> inText
    }

// Minimal XML re-indenter: a newline between adjacent tags, one tab per
// nesting level (rendered at the editor's tab size). Declarations (<?…?>), comments / doctype (<!…>), self-closing tags and
// single-line <a>text</a> elements don't change depth. Best-effort — content
// with a literal '>' inside attributes or CDATA may not round-trip, hence the
// runCatching guard at the call site.
private fun formatXml(inText: String): String {
    val vTrim = inText.trim()
    if (vTrim.isEmpty() || !vTrim.startsWith("<")) return inText
    val vNormalized = Regex(">\\s*<").replace(vTrim, ">\n<")
    val vInline = Regex("^<([\\w:.\\-]+)(\\s[^>]*)?>.*</\\1>$")
    val vSb = StringBuilder()
    var vDepth = 0
    for (vRaw in vNormalized.split("\n")) {
        val vLine = vRaw.trim()
        if (vLine.isEmpty()) continue
        val vIsClose = vLine.startsWith("</")
        val vIsVoid = vLine.startsWith("<?") || vLine.startsWith("<!") || vLine.endsWith("/>")
        val vIsInline = vInline.matches(vLine)
        if (vIsClose) vDepth = (vDepth - 1).coerceAtLeast(0)
        repeat(vDepth) { vSb.append('\t') }
        vSb.append(vLine).append('\n')
        if (vLine.startsWith("<") && !vIsClose && !vIsVoid && !vIsInline) vDepth++
    }
    return vSb.toString().trimEnd('\n')
}
