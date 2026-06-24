package androidx.compose.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Sp

// ==================
// MARK: SpanStyle / ParagraphStyle / TextStyle
// ==================

/* Character-level style applied over a range of an AnnotatedString:
   font colour / size / weight / italic / decoration / background tint /
   inter-letter spacing. Unspecified fields fall through to the
   surrounding TextStyle. */
data class SpanStyle(
	val color: Color = Color.Unspecified,
	val fontSize: Sp = Sp.Unspecified,
	val fontWeight: FontWeight? = null,
	val fontStyle: FontStyle? = null,
	val fontFamily: FontFamily? = null,
	val textDecoration: TextDecoration? = null,
	val background: Color = Color.Unspecified,
	val letterSpacing: Sp = Sp.Unspecified,
)

/* Paragraph-level style: text alignment, line height, max-width-based
   overflow. */
data class ParagraphStyle(
	val textAlign: TextAlign? = null,
	val lineHeight: Sp = Sp.Unspecified,
	val textIndent: Float = 0f,
)

/* Default style for the entire text, combining span- and paragraph-level
   defaults. */
data class TextStyle(
	val color: Color = Color.Unspecified,
	val fontSize: Sp = Sp.Unspecified,
	val fontWeight: FontWeight? = null,
	val fontStyle: FontStyle? = null,
	val fontFamily: FontFamily? = null,
	val textAlign: TextAlign? = null,
	val lineHeight: Sp = Sp.Unspecified,
	val letterSpacing: Sp = Sp.Unspecified,
	val textDecoration: TextDecoration? = null,
) {
	companion object {
		val Default = TextStyle()
	}

	fun toSpanStyle(): SpanStyle = SpanStyle(
		color = color, fontSize = fontSize, fontWeight = fontWeight,
		fontStyle = fontStyle, fontFamily = fontFamily,
		textDecoration = textDecoration, letterSpacing = letterSpacing,
	)
	fun toParagraphStyle(): ParagraphStyle = ParagraphStyle(
		textAlign = textAlign, lineHeight = lineHeight,
	)
}

// ==================
// MARK: Range
// ==================

/* Span [start, end) with an associated style. Used inside AnnotatedString
   to record where each style applies. */
data class Range<T>(val item: T, val start: Int, val end: Int)

// ==================
// MARK: AnnotatedString
// ==================

/* Text + style annotations. Today we render with the FIRST (or default)
   span style applied to the whole string — multi-span rendering is a
   TODO for the SDL3 / Skia text paths. The data model itself is
   complete so app code can be written against the upstream API today;
   the renderer side will catch up. */
class AnnotatedString(
	val text: String,
	val spanStyles: List<Range<SpanStyle>> = emptyList(),
	val paragraphStyles: List<Range<ParagraphStyle>> = emptyList(),
) {

	val length: Int get() = text.length

	override fun toString(): String = text

	override fun equals(other: Any?): Boolean =
		other is AnnotatedString && other.text == text &&
		other.spanStyles == spanStyles && other.paragraphStyles == paragraphStyles

	override fun hashCode(): Int {
		var v = text.hashCode()
		v = 31 * v + spanStyles.hashCode()
		v = 31 * v + paragraphStyles.hashCode()
		return v
	}

	// ============
	//  Builder
	class Builder(inCapacity: Int = 16) {
		private val fSb = StringBuilder(inCapacity)
		private val fSpans = mutableListOf<MutableRange<SpanStyle>>()
		private val fParas = mutableListOf<MutableRange<ParagraphStyle>>()
		private val fSpanStack = ArrayDeque<MutableRange<SpanStyle>>()
		private val fParaStack = ArrayDeque<MutableRange<ParagraphStyle>>()

		val length: Int get() = fSb.length

		fun append(inText: String): Builder { fSb.append(inText); return this }
		fun append(inChar: Char): Builder { fSb.append(inChar); return this }

		fun pushStyle(inStyle: SpanStyle): Int {
			val vR = MutableRange(inStyle, fSb.length, -1)
			fSpans.add(vR)
			fSpanStack.addLast(vR)
			return fSpanStack.size - 1
		}
		fun pushStyle(inStyle: ParagraphStyle): Int {
			val vR = MutableRange(inStyle, fSb.length, -1)
			fParas.add(vR)
			fParaStack.addLast(vR)
			return fParaStack.size - 1
		}
		fun pop() {
			// Pop whichever stack was added most recently. We can't tell
			// them apart from the index alone so we close the SpanStyle
			// stack first (the common case).
			when {
				fSpanStack.isNotEmpty() -> fSpanStack.removeLast().end = fSb.length
				fParaStack.isNotEmpty() -> fParaStack.removeLast().end = fSb.length
			}
		}

		fun toAnnotatedString(): AnnotatedString {
			// Close any unclosed ranges at the current length.
			for (vR in fSpanStack) vR.end = fSb.length
			for (vR in fParaStack) vR.end = fSb.length
			return AnnotatedString(
				text = fSb.toString(),
				spanStyles = fSpans.map { Range(it.item, it.start, it.end) },
				paragraphStyles = fParas.map { Range(it.item, it.start, it.end) },
			)
		}

		private class MutableRange<T>(val item: T, val start: Int, var end: Int)
	}
}

// ==================
// MARK: buildAnnotatedString DSL
// ==================

/* Standard builder DSL: pushStyle / append / pop inside the block. */
inline fun buildAnnotatedString(inBlock: AnnotatedString.Builder.() -> Unit): AnnotatedString {
	val vB = AnnotatedString.Builder()
	vB.inBlock()
	return vB.toAnnotatedString()
}

/* Convenience to wrap a literal string. */
fun AnnotatedString(inText: String): AnnotatedString = AnnotatedString(text = inText)
