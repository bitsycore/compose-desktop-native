package androidx.compose.ui.text

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

// ==================
// MARK: ParagraphIntrinsics + Paragraph factory actuals (SDL)
// ==================

/* Carries text+style for the intrinsics-based Paragraph factories; intrinsic widths come from a
   throwaway unbounded SdlParagraph. `density` here is the LocalDensity scalar (dpr on Retina); it
   converts sp → pixels so the intrinsic widths land in the same pixel space the layout tree measures
   itself in. */
internal class SdlParagraphIntrinsics(
	val paragraphText: String,
	val paragraphStyle: TextStyle,
	val density: Float,
) : ParagraphIntrinsics {
	private val probe = SdlParagraph(paragraphText, paragraphStyle, Float.POSITIVE_INFINITY, Int.MAX_VALUE, density)
	override val minIntrinsicWidth: Float = probe.minIntrinsicWidth
	override val maxIntrinsicWidth: Float = probe.maxIntrinsicWidth
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
): ParagraphIntrinsics = SdlParagraphIntrinsics(text, style, density.density)

actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
): ParagraphIntrinsics = SdlParagraphIntrinsics(text, style, density.density)

actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
): ParagraphIntrinsics = SdlParagraphIntrinsics(text, style, density.density)

actual fun ParagraphIntrinsics(
	text: String,
	style: TextStyle,
	annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
	density: Density,
	fontFamilyResolver: FontFamily.Resolver,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
	softWrap: Boolean,
): ParagraphIntrinsics = SdlParagraphIntrinsics(text, style, density.density)

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
): Paragraph = SdlParagraph(text, style, width, maxLines, density.density)

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
): Paragraph = SdlParagraph(text, style, width, maxLines, density.density)

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
): Paragraph = SdlParagraph(text, style, widthFrom(constraints), maxLines, density.density)

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
): Paragraph = SdlParagraph(text, style, widthFrom(constraints), maxLines, density.density)

actual fun Paragraph(
	paragraphIntrinsics: ParagraphIntrinsics,
	maxLines: Int,
	ellipsis: Boolean,
	width: Float,
): Paragraph {
	val vI = paragraphIntrinsics as SdlParagraphIntrinsics
	return SdlParagraph(vI.paragraphText, vI.paragraphStyle, width, maxLines, vI.density)
}

actual fun Paragraph(
	paragraphIntrinsics: ParagraphIntrinsics,
	constraints: Constraints,
	maxLines: Int,
	ellipsis: Boolean,
): Paragraph {
	val vI = paragraphIntrinsics as SdlParagraphIntrinsics
	return SdlParagraph(vI.paragraphText, vI.paragraphStyle, widthFrom(constraints), maxLines, vI.density)
}

actual fun Paragraph(
	paragraphIntrinsics: ParagraphIntrinsics,
	constraints: Constraints,
	maxLines: Int,
	overflow: TextOverflow,
): Paragraph {
	val vI = paragraphIntrinsics as SdlParagraphIntrinsics
	return SdlParagraph(vI.paragraphText, vI.paragraphStyle, widthFrom(constraints), maxLines, vI.density)
}
