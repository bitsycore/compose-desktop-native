package androidx.compose.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

// ==================
// MARK: SpanStyle / ParagraphStyle / TextStyle
// ==================

// Reshape note (FIDELITY): upstream's SpanStyle / ParagraphStyle / TextStyle
// are plain classes with manual equals/hashCode — no component*/copy. We
// mirror that here.

/* Character-level style applied over a range of an AnnotatedString:
   font colour / size / weight / italic / decoration / background tint /
   inter-letter spacing. Unspecified fields fall through to the
   surrounding TextStyle. */
class SpanStyle(
	val color: Color = Color.Unspecified,
	val fontSize: TextUnit = TextUnit.Unspecified,
	val fontWeight: FontWeight? = null,
	val fontStyle: FontStyle? = null,
	val fontFamily: FontFamily? = null,
	val textDecoration: TextDecoration? = null,
	val background: Color = Color.Unspecified,
	val letterSpacing: TextUnit = TextUnit.Unspecified,
) {
	override fun equals(other: Any?): Boolean = other is SpanStyle &&
		other.color == color &&
		other.fontSize == fontSize &&
		other.fontWeight == fontWeight &&
		other.fontStyle == fontStyle &&
		other.fontFamily == fontFamily &&
		other.textDecoration == textDecoration &&
		other.background == background &&
		other.letterSpacing == letterSpacing
	override fun hashCode(): Int {
		var h = color.hashCode()
		h = 31 * h + fontSize.hashCode()
		h = 31 * h + (fontWeight?.hashCode() ?: 0)
		h = 31 * h + (fontStyle?.hashCode() ?: 0)
		h = 31 * h + (fontFamily?.hashCode() ?: 0)
		h = 31 * h + (textDecoration?.hashCode() ?: 0)
		h = 31 * h + background.hashCode()
		h = 31 * h + letterSpacing.hashCode()
		return h
	}
}

/* Paragraph-level style: text alignment, line height, max-width-based
   overflow. */
class ParagraphStyle(
	val textAlign: TextAlign? = null,
	val lineHeight: TextUnit = TextUnit.Unspecified,
	val textIndent: Float = 0f,
) {
	override fun equals(other: Any?): Boolean = other is ParagraphStyle &&
		other.textAlign == textAlign &&
		other.lineHeight == lineHeight &&
		other.textIndent == textIndent
	override fun hashCode(): Int {
		var h = textAlign?.hashCode() ?: 0
		h = 31 * h + lineHeight.hashCode()
		h = 31 * h + textIndent.hashCode()
		return h
	}
}

/* Default style for the entire text, combining span- and paragraph-level
   defaults. */
class TextStyle(
	val color: Color = Color.Unspecified,
	val fontSize: TextUnit = TextUnit.Unspecified,
	val fontWeight: FontWeight? = null,
	val fontStyle: FontStyle? = null,
	val fontFamily: FontFamily? = null,
	val textAlign: TextAlign? = null,
	val lineHeight: TextUnit = TextUnit.Unspecified,
	val letterSpacing: TextUnit = TextUnit.Unspecified,
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

	override fun equals(other: Any?): Boolean = other is TextStyle &&
		other.color == color &&
		other.fontSize == fontSize &&
		other.fontWeight == fontWeight &&
		other.fontStyle == fontStyle &&
		other.fontFamily == fontFamily &&
		other.textAlign == textAlign &&
		other.lineHeight == lineHeight &&
		other.letterSpacing == letterSpacing &&
		other.textDecoration == textDecoration
	override fun hashCode(): Int {
		var h = color.hashCode()
		h = 31 * h + fontSize.hashCode()
		h = 31 * h + (fontWeight?.hashCode() ?: 0)
		h = 31 * h + (fontStyle?.hashCode() ?: 0)
		h = 31 * h + (fontFamily?.hashCode() ?: 0)
		h = 31 * h + (textAlign?.hashCode() ?: 0)
		h = 31 * h + lineHeight.hashCode()
		h = 31 * h + letterSpacing.hashCode()
		h = 31 * h + (textDecoration?.hashCode() ?: 0)
		return h
	}
}

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
) : CharSequence {

	// CharSequence delegates to the backing text — matches upstream
	// `class AnnotatedString : CharSequence`.
	override val length: Int get() = text.length
	override operator fun get(index: Int): Char = text[index]
	override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
		AnnotatedString(text.substring(startIndex, endIndex))

	/* Span [start, end) with an associated style. Nested under
	   AnnotatedString to match upstream's `AnnotatedString.Range<T>`.
	   `tag` is an optional disambiguator (default empty) used by upstream
	   for annotation kinds — we don't read it from the renderers today
	   but accept it so call sites can target the upstream signature. */
	data class Range<T>(val item: T, val start: Int, val end: Int, val tag: String = "")

	/**
	 * Marker interface for typed annotation payloads (TTS / URL / Link /
	 * String / etc.). Mirrors upstream's nested
	 * `AnnotatedString.Annotation` sealed interface — kept as plain
	 * `interface` here so vendored subclasses (`TtsAnnotation`,
	 * `LinkAnnotation`, `StringAnnotation`, `UrlAnnotation`) can extend
	 * it freely. This renderer doesn't read annotation payloads yet, so
	 * the interface is dormant; presence-only.
	 */
	interface Annotation

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
