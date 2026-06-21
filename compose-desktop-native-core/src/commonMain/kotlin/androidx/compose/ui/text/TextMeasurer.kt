package androidx.compose.ui.text

import androidx.compose.ui.unit.IntSize

// ==================
// MARK: TextMeasurer
// ==================

/* Wrapped layout result. `lines[i]` is the visible text of wrapped line i;
   `lineStarts[i]` is the offset into the original text where that line
   begins. lineStarts[i] + lines[i].length may be less than lineStarts[i+1]
   when the gap contains explicit '\n' characters (which are consumed
   between lines and don't appear in any line's text). */
class WrappedText(val lines: List<String>, val lineStarts: IntArray)

/* Shared abstraction so that the commonMain layout pass (TextMeasurePolicy)
   can get the same width / height that the native renderer will actually
   draw. The native backend installs a Skia-backed implementation at startup. */
interface TextMeasurer {
	/* Measure the text's laid-out size. If inMaxWidth is bounded, lines wrap
	   at word boundaries (or mid-word if a single word exceeds the limit).
	   inFontFamily picks a registered IconFont; null falls back to the
	   renderer's default font. inFontVariations applies variable-font axis
	   settings (Material Symbols wght / FILL / etc.) when supported by the
	   active renderer. */
	fun measure(
		inText: String,
		inFontSize: Int,
		inMaxWidth: Int = Int.MAX_VALUE,
		inFontFamily: String? = null,
		inFontVariations: List<FontVariation>? = null,
	): IntSize

	/* Wrapped lines + the original-text offset where each begins. */
	fun wrap(
		inText: String,
		inFontSize: Int,
		inMaxWidth: Int = Int.MAX_VALUE,
		inFontFamily: String? = null,
		inFontVariations: List<FontVariation>? = null,
	): WrappedText

	/* Exact line height the renderer uses for this fontSize, as a Float so
	   callers (TextField cursor / click math) line up with rendered glyph
	   slots even when the per-line drift is sub-pixel. */
	fun lineHeight(
		inFontSize: Int,
		inFontFamily: String? = null,
		inFontVariations: List<FontVariation>? = null,
	): Float
}

// ==================
// MARK: Default fallback
// ==================

private val kFallbackTextMeasurer = object : TextMeasurer {
	override fun measure(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<FontVariation>?): IntSize {
		val vCharW = (inFontSize * 0.6f).toInt().coerceAtLeast(1)
		return IntSize(vCharW * inText.length, (inFontSize * 1.3f).toInt())
	}
	override fun wrap(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<FontVariation>?): WrappedText {
		val vLines = if (inText.isEmpty()) listOf("") else inText.split('\n')
		val vStarts = IntArray(vLines.size)
		var vAcc = 0
		for (i in vLines.indices) {
			vStarts[i] = vAcc
			vAcc += vLines[i].length + 1 // +1 for the consumed '\n' between hard lines
		}
		return WrappedText(vLines, vStarts)
	}
	override fun lineHeight(inFontSize: Int, inFontFamily: String?, inFontVariations: List<FontVariation>?): Float = inFontSize * 1.3f
}

var currentTextMeasurer: TextMeasurer = kFallbackTextMeasurer
