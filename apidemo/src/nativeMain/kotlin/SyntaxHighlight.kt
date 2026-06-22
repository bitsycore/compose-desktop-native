package apidemo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

// ==================
// MARK: BodyFormat — viewer/highlighter selection
// ==================

/* Format of a body payload as shown in the viewer. RAW disables
   highlighting and falls back to the selectable BasicTextField. Other
   values pick a tokeniser and render via Text(AnnotatedString). */
enum class BodyFormat { RAW, JSON, XML, YAML, HTML }

/* Pick a format from a Content-Type header. JSON / XML / YAML / HTML
   match their canonical types and common variants; anything else
   (image types, text/plain, …) falls back to RAW. */
fun autoFormatFor(inContentType: String?): BodyFormat {
	val vCt = inContentType?.substringBefore(';')?.trim()?.lowercase() ?: return BodyFormat.RAW
	return when {
		vCt.endsWith("+json") || vCt == "application/json"          -> BodyFormat.JSON
		vCt.endsWith("+xml")  || vCt == "application/xml" || vCt == "text/xml" -> BodyFormat.XML
		vCt == "application/yaml" || vCt == "text/yaml" || vCt == "application/x-yaml" -> BodyFormat.YAML
		vCt == "text/html"                                           -> BodyFormat.HTML
		else                                                          -> BodyFormat.RAW
	}
}

// ==================
// MARK: Highlight palette
// ==================

/* Per-token colours used across all three highlighters. Tuned for the
   apidemo dark theme; light theme will look acceptable but not tuned. */
private object SynColors {
	val Key      = Color(0xFF9CDCFE) // light blue — JSON keys, XML attr names, YAML keys
	val String   = Color(0xFFCE9178) // peach — quoted strings
	val Number   = Color(0xFFB5CEA8) // sage — numeric literals
	val Keyword  = Color(0xFFC586C0) // pink — true / false / null
	val Tag      = Color(0xFF569CD6) // brighter blue — XML/HTML element names
	val Comment  = Color(0xFF6A9955) // green — // and #
	val Punct    = Color(0xFFCCCCCC) // light gray — braces, colons, commas
}

// ==================
// MARK: Entry point
// ==================

/* Render the given text with per-token colour spans for the requested
   format. RAW returns an unstyled AnnotatedString. */
fun highlight(inText: String, inFormat: BodyFormat): AnnotatedString = when (inFormat) {
	BodyFormat.RAW  -> AnnotatedString(inText)
	BodyFormat.JSON -> highlightJson(inText)
	BodyFormat.XML, BodyFormat.HTML -> highlightXml(inText)
	BodyFormat.YAML -> highlightYaml(inText)
}

// ==================
// MARK: JSON tokeniser — single-pass, error-tolerant
// ==================

/* Tokeniser for JSON. Walks the string once and emits style spans for
   strings, numbers, booleans, null, punctuation. Distinguishes "keys"
   (strings followed by ':') from string values by peeking after each
   string. Error-tolerant — malformed JSON still highlights as best
   effort rather than throwing. */
private fun highlightJson(inText: String): AnnotatedString = buildAnnotatedString {
	val vN = inText.length
	var vI = 0
	while (vI < vN) {
		val vC = inText[vI]
		when {
			vC == '"' -> {
				// Find closing quote, honouring backslash-escaped chars so an
				// escaped \" doesn't terminate the string early.
				val vStart = vI
				var vJ = vI + 1
				while (vJ < vN) {
					if (inText[vJ] == '\\' && vJ + 1 < vN) { vJ += 2; continue }
					if (inText[vJ] == '"') { vJ++; break }
					vJ++
				}
				// Peek for ':' (skipping whitespace) → it's a key, not a value.
				var vK = vJ
				while (vK < vN && inText[vK].isWhitespace()) vK++
				val vIsKey = vK < vN && inText[vK] == ':'
				val vColor = if (vIsKey) SynColors.Key else SynColors.String
				pushStyle(SpanStyle(color = vColor))
				append(inText.substring(vStart, vJ))
				pop()
				vI = vJ
			}
			vC.isDigit() || (vC == '-' && vI + 1 < vN && inText[vI + 1].isDigit()) -> {
				val vStart = vI
				if (vC == '-') vI++
				while (vI < vN && (inText[vI].isDigit() || inText[vI] in ".eE+-")) vI++
				pushStyle(SpanStyle(color = SynColors.Number))
				append(inText.substring(vStart, vI))
				pop()
			}
			vC.isLetter() -> {
				val vStart = vI
				while (vI < vN && inText[vI].isLetter()) vI++
				val vWord = inText.substring(vStart, vI)
				if (vWord == "true" || vWord == "false" || vWord == "null") {
					pushStyle(SpanStyle(color = SynColors.Keyword))
					append(vWord)
					pop()
				} else {
					append(vWord)
				}
			}
			vC in "{}[],:" -> {
				pushStyle(SpanStyle(color = SynColors.Punct))
				append(vC)
				pop()
				vI++
			}
			else -> { append(vC); vI++ }
		}
	}
}

// ==================
// MARK: XML / HTML tokeniser
// ==================

/* Tokeniser for XML / HTML. Distinguishes tag names, attribute names,
   attribute values (string), and comments. Inside tags, attr=value pairs
   are recognised; outside tags text is left in the default colour. */
private fun highlightXml(inText: String): AnnotatedString = buildAnnotatedString {
	val vN = inText.length
	var vI = 0
	while (vI < vN) {
		val vC = inText[vI]
		when {
			// Comment block: <!-- ... -->
			vC == '<' && inText.startsWith("<!--", vI) -> {
				val vEnd = inText.indexOf("-->", vI + 4).let { if (it < 0) vN else it + 3 }
				pushStyle(SpanStyle(color = SynColors.Comment))
				append(inText.substring(vI, vEnd))
				pop()
				vI = vEnd
			}
			vC == '<' -> {
				// Tag open: <[/]name attr=val attr2="val2">
				val vGt = inText.indexOf('>', vI).let { if (it < 0) vN else it + 1 }
				pushStyle(SpanStyle(color = SynColors.Punct)); append('<'); pop()
				var vJ = vI + 1
				if (vJ < vN && inText[vJ] == '/') { pushStyle(SpanStyle(color = SynColors.Punct)); append('/'); pop(); vJ++ }
				// Tag name.
				val vTagStart = vJ
				while (vJ < vN && !inText[vJ].isWhitespace() && inText[vJ] != '>' && inText[vJ] != '/') vJ++
				if (vJ > vTagStart) {
					pushStyle(SpanStyle(color = SynColors.Tag))
					append(inText.substring(vTagStart, vJ))
					pop()
				}
				// Attributes — name="value" pairs.
				while (vJ < vGt - 1) {
					val vC2 = inText[vJ]
					when {
						vC2.isWhitespace() -> { append(vC2); vJ++ }
						vC2 == '/' -> { pushStyle(SpanStyle(color = SynColors.Punct)); append('/'); pop(); vJ++ }
						vC2 == '=' -> { pushStyle(SpanStyle(color = SynColors.Punct)); append('='); pop(); vJ++ }
						vC2 == '"' || vC2 == '\'' -> {
							val vEnd = inText.indexOf(vC2, vJ + 1).let { if (it < 0) vGt - 1 else it + 1 }
							pushStyle(SpanStyle(color = SynColors.String))
							append(inText.substring(vJ, vEnd))
							pop()
							vJ = vEnd
						}
						else -> {
							val vAttrStart = vJ
							while (vJ < vGt - 1 && !inText[vJ].isWhitespace() && inText[vJ] != '=' && inText[vJ] != '>' && inText[vJ] != '/') vJ++
							pushStyle(SpanStyle(color = SynColors.Key))
							append(inText.substring(vAttrStart, vJ))
							pop()
						}
					}
				}
				if (vGt > vI && inText.getOrNull(vGt - 1) == '>') {
					pushStyle(SpanStyle(color = SynColors.Punct)); append('>'); pop()
				}
				vI = vGt
			}
			else -> { append(vC); vI++ }
		}
	}
}

// ==================
// MARK: YAML tokeniser — line-based
// ==================

/* Tokeniser for YAML. Walks line by line because YAML's grammar is
   indent-sensitive and most of the interesting colour decisions
   (key vs value, list item vs scalar) depend on position within a
   line. Comments (#), keys (text before ':'), quoted strings, numbers
   and booleans are coloured; everything else stays default. */
private fun highlightYaml(inText: String): AnnotatedString = buildAnnotatedString {
	val vLines = inText.split('\n')
	for ((vIdx, vLineRaw) in vLines.withIndex()) {
		val vLine = vLineRaw
		// Comment: rest of the line after a # (not inside a quoted string).
		val vCommentAt = findUnquotedHash(vLine)
		val vCodePart = if (vCommentAt >= 0) vLine.substring(0, vCommentAt) else vLine
		val vCommentPart = if (vCommentAt >= 0) vLine.substring(vCommentAt) else ""

		// Find a key: indent + (optional "- ") + key + ':' + (space|eol).
		val vColon = indexOfKeyColon(vCodePart)
		if (vColon > 0) {
			val vPrefix = vCodePart.substring(0, vColon)
			val vRest = vCodePart.substring(vColon + 1)
			// Lead whitespace + optional list marker, then key.
			var vK = 0
			while (vK < vPrefix.length && vPrefix[vK].isWhitespace()) { append(vPrefix[vK]); vK++ }
			if (vK + 1 < vPrefix.length && vPrefix[vK] == '-' && vPrefix[vK + 1].isWhitespace()) {
				pushStyle(SpanStyle(color = SynColors.Punct)); append('-'); pop()
				append(vPrefix[vK + 1])
				vK += 2
			}
			pushStyle(SpanStyle(color = SynColors.Key))
			append(vPrefix.substring(vK))
			pop()
			pushStyle(SpanStyle(color = SynColors.Punct)); append(':'); pop()
			appendYamlValue(vRest)
		} else {
			appendYamlValue(vCodePart)
		}
		if (vCommentPart.isNotEmpty()) {
			pushStyle(SpanStyle(color = SynColors.Comment))
			append(vCommentPart)
			pop()
		}
		if (vIdx < vLines.size - 1) append('\n')
	}
}

/* Render the value portion of a YAML line — applies string / number /
   keyword / list-marker colours where applicable. */
private fun AnnotatedString.Builder.appendYamlValue(inSeg: String) {
	val vTrimmed = inSeg.trimStart()
	val vPad = inSeg.substring(0, inSeg.length - vTrimmed.length)
	append(vPad)
	when {
		vTrimmed.isEmpty() -> { /* nothing */ }
		vTrimmed.startsWith('-') && (vTrimmed.length == 1 || vTrimmed[1].isWhitespace()) -> {
			pushStyle(SpanStyle(color = SynColors.Punct)); append('-'); pop()
			appendYamlValue(vTrimmed.substring(1))
		}
		vTrimmed.startsWith('"') || vTrimmed.startsWith('\'') -> {
			val vQ = vTrimmed[0]
			val vEnd = vTrimmed.indexOf(vQ, 1).let { if (it < 0) vTrimmed.length else it + 1 }
			pushStyle(SpanStyle(color = SynColors.String))
			append(vTrimmed.substring(0, vEnd))
			pop()
			append(vTrimmed.substring(vEnd))
		}
		vTrimmed == "true" || vTrimmed == "false" || vTrimmed == "null" || vTrimmed == "~" -> {
			pushStyle(SpanStyle(color = SynColors.Keyword)); append(vTrimmed); pop()
		}
		vTrimmed.first().let { it.isDigit() || it == '-' && vTrimmed.length > 1 && vTrimmed[1].isDigit() } -> {
			var vJ = 0
			if (vTrimmed[0] == '-') vJ++
			while (vJ < vTrimmed.length && (vTrimmed[vJ].isDigit() || vTrimmed[vJ] in ".eE+-")) vJ++
			pushStyle(SpanStyle(color = SynColors.Number))
			append(vTrimmed.substring(0, vJ))
			pop()
			append(vTrimmed.substring(vJ))
		}
		else -> append(vTrimmed)
	}
}

/* Find the index of the first '#' that isn't inside a quoted string,
   or -1 if there is none. Used to split a YAML line into code + comment. */
private fun findUnquotedHash(inLine: String): Int {
	var vInSingle = false
	var vInDouble = false
	for (vI in inLine.indices) {
		val vC = inLine[vI]
		when {
			vC == '"' && !vInSingle -> vInDouble = !vInDouble
			vC == '\'' && !vInDouble -> vInSingle = !vInSingle
			vC == '#' && !vInSingle && !vInDouble -> return vI
		}
	}
	return -1
}

/* Locate the colon that separates a YAML key from its value at the
   current indent level — the FIRST unquoted ':' that's followed by
   either whitespace or end-of-line. Returns -1 if the line has no key. */
private fun indexOfKeyColon(inLine: String): Int {
	var vInSingle = false
	var vInDouble = false
	for (vI in inLine.indices) {
		val vC = inLine[vI]
		when {
			vC == '"' && !vInSingle -> vInDouble = !vInDouble
			vC == '\'' && !vInDouble -> vInSingle = !vInSingle
			vC == ':' && !vInSingle && !vInDouble -> {
				val vNext = if (vI + 1 < inLine.length) inLine[vI + 1] else ' '
				if (vNext.isWhitespace() || vI + 1 == inLine.length) return vI
			}
		}
	}
	return -1
}
