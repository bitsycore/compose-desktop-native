package com.compose.sdl.res

// ==================
// MARK: AndroidVectorToSvg
// ==================

/* Converts a subset of the Android vector drawable XML format into an
   equivalent SVG string both render backends can rasterise. Android's
   pathData uses the exact same path mini-language as SVG's `d`, so the bulk
   of the work is attribute renaming + colour/alpha translation.

   Supported: <vector> width / height / viewport*, any number of <path>
   elements (anywhere in the tree) with fillColor / fillAlpha / fillType /
   strokeColor / strokeWidth / strokeAlpha / strokeLineCap / strokeLineJoin.

   Not supported: <group> transforms (paths are flattened, transforms ignored),
   gradients, clip-paths, trim-path. This covers the common single-/multi-path
   icon case such as exported Material icons. */
object AndroidVectorToSvg {

	// Tag values never contain '>' (pathData is letters/digits/.,- /space) and
	// quoted attribute values can't either, so [^>] within a tag is safe.
	private val kTagRegex = Regex("<(vector|path)\\b([^>]*?)/?>")
	private val kAttrRegex = Regex("([\\w:]+)\\s*=\\s*\"([^\"]*)\"")

	/* Returns an SVG document string. Falls back to a 24×24 viewport when the
	   source omits sizes. */
	fun convert(inXml: String): String {
		var vW = 24f
		var vH = 24f
		var vVbW = -1f
		var vVbH = -1f
		val vPaths = StringBuilder()

		for (vMatch in kTagRegex.findAll(inXml)) {
			val vTag = vMatch.groupValues[1]
			val vAttrs = parseAttrs(vMatch.groupValues[2])
			when (vTag) {
				"vector" -> {
					dimen(vAttrs["width"])?.let { vW = it }
					dimen(vAttrs["height"])?.let { vH = it }
					vAttrs["viewportWidth"]?.toFloatOrNull()?.let { vVbW = it }
					vAttrs["viewportHeight"]?.toFloatOrNull()?.let { vVbH = it }
				}
				"path" -> {
					val vData = vAttrs["pathData"] ?: continue
					vPaths.append(pathToSvg(vData, vAttrs)).append('\n')
				}
			}
		}

		// Viewport defaults to the declared pixel size when absent.
		if (vVbW <= 0f) vVbW = vW
		if (vVbH <= 0f) vVbH = vH

		return buildString {
			append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
			append("width=\"").append(fmt(vW)).append("\" ")
			append("height=\"").append(fmt(vH)).append("\" ")
			append("viewBox=\"0 0 ").append(fmt(vVbW)).append(' ').append(fmt(vVbH)).append("\">\n")
			append(vPaths)
			append("</svg>")
		}
	}

	/* name="value" pairs → map keyed by local name (android:/aapt: prefix stripped). */
	private fun parseAttrs(inTagBody: String): Map<String, String> {
		val vOut = HashMap<String, String>()
		for (vMatch in kAttrRegex.findAll(inTagBody)) {
			val vName = vMatch.groupValues[1].substringAfterLast(':')
			vOut[vName] = vMatch.groupValues[2]
		}
		return vOut
	}

	private fun pathToSvg(inData: String, inAttrs: Map<String, String>): String {
		val vSb = StringBuilder("  <path d=\"").append(inData.trim()).append('"')

		// ============
		//  Fill
		val vFill = inAttrs["fillColor"]
		if (vFill == null) {
			vSb.append(" fill=\"none\"")
		} else {
			val (vHex, vOpacity) = colorToSvg(vFill)
			vSb.append(" fill=\"").append(vHex).append('"')
			// Explicit fillAlpha wins over any alpha baked into the colour.
			val vAlpha = inAttrs["fillAlpha"]?.toFloatOrNull() ?: vOpacity
			if (vAlpha != null) vSb.append(" fill-opacity=\"").append(fmt(vAlpha)).append('"')
		}
		if (inAttrs["fillType"]?.equals("evenOdd", ignoreCase = true) == true) {
			vSb.append(" fill-rule=\"evenodd\"")
		}

		// ============
		//  Stroke
		val vStroke = inAttrs["strokeColor"]
		if (vStroke != null) {
			val (vHex, vOpacity) = colorToSvg(vStroke)
			vSb.append(" stroke=\"").append(vHex).append('"')
			val vAlpha = inAttrs["strokeAlpha"]?.toFloatOrNull() ?: vOpacity
			if (vAlpha != null) vSb.append(" stroke-opacity=\"").append(fmt(vAlpha)).append('"')
			inAttrs["strokeWidth"]?.toFloatOrNull()?.let {
				vSb.append(" stroke-width=\"").append(fmt(it)).append('"')
			}
			inAttrs["strokeLineCap"]?.let { vSb.append(" stroke-linecap=\"").append(it.lowercase()).append('"') }
			inAttrs["strokeLineJoin"]?.let { vSb.append(" stroke-linejoin=\"").append(it.lowercase()).append('"') }
		}

		vSb.append("/>")
		return vSb.toString()
	}

	// ==================
	// MARK: Colour / dimension helpers
	// ==================

	/* Android colour string → (svg "#RRGGBB", opacity 0..1 or null).
	   Accepts #RGB, #ARGB, #RRGGBB, #AARRGGBB. Unknown forms fall back to black. */
	private fun colorToSvg(inColor: String): Pair<String, Float?> {
		if (!inColor.startsWith("#")) return "#000000" to null
		val vHex = inColor.substring(1)
		return when (vHex.length) {
			3 -> "#" + vHex.map { "$it$it" }.joinToString("") to null            // RGB
			4 -> {                                                               // ARGB
				val vRgb = "#" + vHex.substring(1).map { "$it$it" }.joinToString("")
				vRgb to (nibble(vHex[0]) / 15f)
			}
			6 -> "#$vHex" to null                                                // RRGGBB
			8 -> "#${vHex.substring(2)}" to (byteHex(vHex.substring(0, 2)) / 255f) // AARRGGBB
			else -> "#000000" to null
		}
	}

	private fun nibble(inCh: Char): Int = inCh.digitToIntOrNull(16) ?: 0
	private fun byteHex(inHex: String): Int = inHex.toIntOrNull(16) ?: 0

	/* "24dp" / "24dip" / "24px" / "24" → 24f. */
	private fun dimen(inValue: String?): Float? {
		if (inValue == null) return null
		val vNum = inValue.trimEnd('d', 'i', 'p', 'x', 'D', 'I', 'P', 'X', ' ')
		return vNum.toFloatOrNull()
	}

	/* Drops a trailing ".0" so the SVG reads "24" not "24.0". */
	private fun fmt(inValue: Float): String =
		if (inValue == inValue.toInt().toFloat()) inValue.toInt().toString() else inValue.toString()
}
