package androidx.compose.ui.text

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

// ==================
// MARK: ParagraphIntrinsics + Paragraph factory actuals (skiko engine)
// ==================
//
// The `Paragraph()` / `ParagraphIntrinsics()` factories (expects in vendored
// commonMain) resolve here to the skiko-backed engine. Because `:ui`'s native
// hierarchy is nativeMain -> skiko (inverted vs upstream skiko -> native), the
// real SkiaParagraph lives in the child skiko source set; these parent-level
// actuals bridge to it through `makeSkiaParagraph` / `paragraphIntrinsicWidths`,
// whose actuals sit in skikoRendererMain (compiled into both the official-skiko
// and mingw-fork siblings). Same pattern as createRenderBackend.

/** Construct the nativeMain [SkiaParagraph] over a skiko-backed ops seam. */
private fun makeSkiaParagraph(
	text: String,
	style: TextStyle,
	width: Float,
	maxLines: Int,
	ellipsize: Boolean,
	density: Float,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
): Paragraph = SkiaParagraph(
	text, style, width,
	buildParagraphOps(text, style, width, maxLines, ellipsize, density, spanStyles),
)

/** [min, max] intrinsic width from a throwaway unbounded skiko layout. */
internal expect fun paragraphIntrinsicWidths(
	text: String,
	style: TextStyle,
	density: Float,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
): FloatArray

/** Carries text+style for the intrinsics-based Paragraph factories; intrinsic
   widths come from an unbounded skiko layout. `density` is the LocalDensity
   scalar (dpr on Retina): it converts sp → pixels so intrinsic widths land in
   the same physical-pixel space the layout tree measures in. */
internal class NativeParagraphIntrinsics(
	val paragraphText: String,
	val paragraphStyle: TextStyle,
	val density: Float,
	val spanStyles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
) : ParagraphIntrinsics {
	private val widths = paragraphIntrinsicWidths(paragraphText, paragraphStyle, density, spanStyles)
	override val minIntrinsicWidth: Float = widths[0]
	override val maxIntrinsicWidth: Float = widths[1]
	override val hasStaleResolvedFonts: Boolean = false
}

private fun widthFrom(constraints: Constraints): Float =
	if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY

// ---- ParagraphIntrinsics factories ----

@Suppress("DEPRECATION")
actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	density: Density,
	resourceLoader: Font.ResourceLoader,
): ParagraphIntrinsics = NativeParagraphIntrinsics(text, style, density.density, spanStyles)

actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
): ParagraphIntrinsics = NativeParagraphIntrinsics(text, style, density.density, spanStyles)

actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
): ParagraphIntrinsics = NativeParagraphIntrinsics(text, style, density.density, annotations.filterSpanStyles())

actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	softWrap: Boolean,
): ParagraphIntrinsics = NativeParagraphIntrinsics(text, style, density.density, annotations.filterSpanStyles())

// AnnotatedString.Annotation is a sealed interface (SpanStyle, ParagraphStyle,
// LinkAnnotation, StringAnnotation, TtsAnnotation, …). Only SpanStyle affects
// glyph layout/painting.
@Suppress("UNCHECKED_CAST")
private fun List<AnnotatedString.Range<out AnnotatedString.Annotation>>.filterSpanStyles():
	List<AnnotatedString.Range<SpanStyle>> =
	filter { it.item is SpanStyle } as List<AnnotatedString.Range<SpanStyle>>

// ---- Paragraph factories ----

@Suppress("DEPRECATION")
actual fun Paragraph(
	text: String,
	style: TextStyle,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	maxLines: Int,
	ellipsis: Boolean,
	width: Float,
	density: Density,
	resourceLoader: Font.ResourceLoader,
): Paragraph = makeSkiaParagraph(text, style, width, maxLines, ellipsis, density.density, spanStyles)

actual fun Paragraph(
	text: String,
	style: TextStyle,
	width: Float,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	maxLines: Int,
	ellipsis: Boolean,
): Paragraph = makeSkiaParagraph(text, style, width, maxLines, ellipsis, density.density, spanStyles)

actual fun Paragraph(
	text: String,
	style: TextStyle,
	constraints: Constraints,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	maxLines: Int,
	ellipsis: Boolean,
): Paragraph = makeSkiaParagraph(text, style, widthFrom(constraints), maxLines, ellipsis, density.density, spanStyles)

actual fun Paragraph(
	text: String,
	style: TextStyle,
	constraints: Constraints,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	maxLines: Int,
	overflow: TextOverflow,
): Paragraph = makeSkiaParagraph(
	text, style, widthFrom(constraints), maxLines, overflow == TextOverflow.Ellipsis, density.density, spanStyles,
)

actual fun Paragraph(
	paragraphIntrinsics: ParagraphIntrinsics,
	maxLines: Int,
	ellipsis: Boolean,
	width: Float,
): Paragraph {
	val i = paragraphIntrinsics as NativeParagraphIntrinsics
	return makeSkiaParagraph(i.paragraphText, i.paragraphStyle, width, maxLines, ellipsis, i.density, i.spanStyles)
}

actual fun Paragraph(
	paragraphIntrinsics: ParagraphIntrinsics,
	constraints: Constraints,
	maxLines: Int,
	ellipsis: Boolean,
): Paragraph {
	val i = paragraphIntrinsics as NativeParagraphIntrinsics
	return makeSkiaParagraph(i.paragraphText, i.paragraphStyle, widthFrom(constraints), maxLines, ellipsis, i.density, i.spanStyles)
}

actual fun Paragraph(
	paragraphIntrinsics: ParagraphIntrinsics,
	constraints: Constraints,
	maxLines: Int,
	overflow: TextOverflow,
): Paragraph {
	val i = paragraphIntrinsics as NativeParagraphIntrinsics
	return makeSkiaParagraph(
		i.paragraphText, i.paragraphStyle, widthFrom(constraints), maxLines,
		overflow == TextOverflow.Ellipsis, i.density, i.spanStyles,
	)
}
