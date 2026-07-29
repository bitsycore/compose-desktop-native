package androidx.compose.ui.text

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import com.compose.sdl.renderer.skia.SkiaFonts
import com.compose.sdl.text.projectFontName
import com.compose.sdl.text.projectFontVariations
import org.jetbrains.skia.Font as SkFont
import org.jetbrains.skia.FontEdging as SkFontEdging
import org.jetbrains.skia.FontHinting as SkFontHinting
import org.jetbrains.skia.FontStyle as SkFontStyle
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.paragraph.Alignment as SkAlignment
import org.jetbrains.skia.paragraph.DecorationLineStyle as SkDecorationLineStyle
import org.jetbrains.skia.paragraph.DecorationStyle as SkDecorationStyle
import org.jetbrains.skia.paragraph.Direction as SkDirection
import org.jetbrains.skia.paragraph.Paragraph as SkParagraph
import org.jetbrains.skia.paragraph.ParagraphBuilder as SkParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.RectHeightMode
import org.jetbrains.skia.paragraph.RectWidthMode
import org.jetbrains.skia.paragraph.Shadow as SkShadow
import org.jetbrains.skia.paragraph.TextStyle as SkTextStyle

// ==================
// MARK: SkiaParagraphOps — skiko skparagraph impl behind the nativeMain seam
// ==================
//
// Lives in the skiko source set (skiko on classpath) and implements the
// skiko-free [NativeParagraphOps] the nativeMain [SkiaParagraph] drives. Builds
// a skiko `org.jetbrains.skia.paragraph` (HarfBuzz shaping + skunicode bidi +
// FontCollection fallback) from Compose style/spans and exposes plain-typed
// queries. Compose style/span -> skiko conversion is a reduced local version of
// upstream ParagraphBuilder.skiko.kt, keeping the port's family/variable-axis
// font model (SkiaFonts) so icons / monospace / custom fonts are unchanged.

private const val INTRINSIC_WIDTH = 100_000f

// ==================
// MARK: Rasterization defaults
// ==================
//
// Edging / hinting / subpixel positioning applied to every text style, mirroring
// upstream ParagraphBuilder.skiko.kt (which sets these on every SkTextStyle).
// The values come from the port's FontRasterizationSettings.PlatformDefault
// (per-OS). Without them skiko falls back to its raw defaults and text renders
// less crisp — noticeably so on Windows/Linux. Computed once (platform is fixed).

@OptIn(ExperimentalTextApi::class)
private val RASTER_EDGING: SkFontEdging = when (FontRasterizationSettings.PlatformDefault.smoothing) {
	FontSmoothing.None -> SkFontEdging.ALIAS
	FontSmoothing.AntiAlias -> SkFontEdging.ANTI_ALIAS
	FontSmoothing.SubpixelAntiAlias -> SkFontEdging.SUBPIXEL_ANTI_ALIAS
}

@OptIn(ExperimentalTextApi::class)
private val RASTER_HINTING: SkFontHinting = when (FontRasterizationSettings.PlatformDefault.hinting) {
	FontHinting.None -> SkFontHinting.NONE
	FontHinting.Slight -> SkFontHinting.SLIGHT
	FontHinting.Normal -> SkFontHinting.NORMAL
	FontHinting.Full -> SkFontHinting.FULL
}

@OptIn(ExperimentalTextApi::class)
private val RASTER_SUBPIXEL: Boolean = FontRasterizationSettings.PlatformDefault.subpixelPositioning

internal class SkiaParagraphOps(
	private val text: String,
	private val style: TextStyle,
	private val widthConstraint: Float,
	private val maxLines: Int,
	private val density: Float,
	private val spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	private val ellipsize: Boolean,
) : NativeParagraphOps {

	private val fontPx: Float =
		((if (style.fontSize.isSpecified) style.fontSize.value else 14f) * density).coerceAtLeast(1f)
	private val baseFamily: String? = style.fontFamily.projectFontName()
	// Icon families (Material Symbols) carry explicit axes on the family; ordinary
	// text derives only the wght axis from the paragraph FontWeight.
	private val baseVariations: List<FontVariation.Setting>? =
		style.fontFamily.projectFontVariations()
			?: style.fontWeight?.let { listOf(FontVariation.weight(it.weight)) }
	private val baseTypeface = SkiaFonts.resolve(baseFamily, baseVariations).first
	private val defaultFont = SkFont(baseTypeface ?: SkiaFonts.defaultTypeface, fontPx)
	private val layoutWidth: Float =
		if (widthConstraint.isFinite() && widthConstraint > 0f) widthConstraint else INTRINSIC_WIDTH

	private var paragraph: SkParagraph = build(style.color, style.shadow, style.textDecoration)

	// The paint attributes the current [paragraph] was built with. Compose paints
	// with paint-time color/shadow/decoration; when they match what we already
	// laid out (the common case — static text), paint reuses the existing native
	// paragraph instead of re-shaping. The constructor above builds with the
	// style values, so seed these to match.
	private var builtColor: Color = style.color
	private var builtShadow: Shadow? = style.shadow
	private var builtDecoration: TextDecoration? = style.textDecoration
	private var disposed = false
	// Reused across color-only repaints (see rebuildAndPaint's fast path).
	private var foregroundPaint: SkPaint? = null

	// ============
	//  NativeParagraphOps

	override val height: Float get() = paragraph.height
	override val lineNumber: Int get() = paragraph.lineNumber
	override val minIntrinsicWidth: Float get() = paragraph.minIntrinsicWidth
	override val maxIntrinsicWidth: Float get() = paragraph.maxIntrinsicWidth
	override val didExceedMaxLines: Boolean get() = paragraph.didExceedMaxLines()
	override val alphabeticBaseline: Float get() = paragraph.alphabeticBaseline
	override val defaultAscentPx: Float get() = -defaultFont.metrics.ascent
	override val defaultDescentPx: Float get() = defaultFont.metrics.descent

	override fun lineMetrics(): List<LineMetricData> {
		val metrics = if (text.isEmpty()) emptyList() else paragraph.lineMetrics.toList()
		if (metrics.isEmpty()) {
			val a = -defaultFont.metrics.ascent.toDouble()
			val d = defaultFont.metrics.descent.toDouble()
			return listOf(
				LineMetricData(0, 0, 0, 0, true, a, d, paragraph.alphabeticBaseline.toDouble().takeIf { it > 0 } ?: a,
					0.0, 0.0, 0.0, a + d, 0),
			)
		}
		return metrics.map {
			LineMetricData(
				startIndex = it.startIndex, endIndex = it.endIndex,
				endExcludingWhitespaces = it.endExcludingWhitespaces, endIncludingNewline = it.endIncludingNewline,
				isHardBreak = it.isHardBreak, ascent = it.ascent, descent = it.descent, baseline = it.baseline,
				left = it.left, right = it.left + it.width, width = it.width, height = it.height,
				lineNumber = it.lineNumber,
			)
		}
	}

	override fun placeholderRects(): List<RectData?> =
		paragraph.rectsForPlaceholders.map { RectData(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom) }

	override fun getRectsForRange(start: Int, end: Int, useMaxHeight: Boolean): List<TextBoxData> =
		paragraph.getRectsForRange(
			start, end,
			if (useMaxHeight) RectHeightMode.MAX else RectHeightMode.STRUT,
			RectWidthMode.TIGHT,
		).map { TextBoxData(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom, it.direction == SkDirection.RTL) }

	override fun glyphPositionAtCoordinate(x: Float, y: Float): Int =
		paragraph.getGlyphPositionAtCoordinate(x, y).position

	override fun wordBoundary(offset: Int): IntArray =
		paragraph.getWordBoundary(offset).let { intArrayOf(it.start, it.end) }

	override fun rebuildAndPaint(canvas: Canvas, color: Color, shadow: Shadow?, decoration: TextDecoration?) {
		val shadowOrDecoChanged = shadow != builtShadow || decoration != builtDecoration
		val colorChanged = color != builtColor
		// Fast path (mirrors upstream ParagraphLayouter): a color-only change on
		// single-style, undecorated text re-applies the foreground paint without
		// re-shaping (HarfBuzz + line-break stay cached). Anything else — a
		// shadow/decoration change, or color on spanned/decorated text where the
		// colour is baked per-run — takes the full rebuild, closing the previous
		// native paragraph so it doesn't leak to GC (issue #2).
		val canUpdateForeground = colorChanged && !shadowOrDecoChanged &&
			spanStyles.isEmpty() && text.isNotEmpty() &&
			(decoration == null || decoration == TextDecoration.None)
		when {
			shadowOrDecoChanged || (colorChanged && !canUpdateForeground) -> {
				val previous = paragraph
				paragraph = build(color, shadow, decoration)
				builtColor = color; builtShadow = shadow; builtDecoration = decoration
				previous.close()
			}
			canUpdateForeground -> {
				applyForegroundColor(color)
				paragraph.markDirty()
				paragraph.layout(layoutWidth)
				builtColor = color
			}
		}
		paragraph.paint(canvas.skiaCanvas, 0f, 0f)
	}

	/** Re-colour the whole (single-style) paragraph in place via skia's
	   updateForegroundPaint, reusing one Paint instance across repaints. */
	private fun applyForegroundColor(color: Color) {
		val argb = (if (color.isSpecified) color else Color.Black).toArgb()
		val paint = foregroundPaint ?: SkPaint().also { foregroundPaint = it }
		paint.reset()
		paint.color = argb
		paragraph.updateForegroundPaint(0, text.length, paint)
	}

	// ============
	//  Compose style/span -> skiko build

	private fun build(color: Color, shadow: Shadow?, decoration: TextDecoration?): SkParagraph {
		val baseStyle = makeTextStyle(baseFamily, baseVariations, fontPx, color, style.fontStyle, decoration, shadow)
		val pStyle = ParagraphStyle().apply {
			alignment = style.textAlign.toSkAlignment()
			direction = if (style.textDirection == androidx.compose.ui.text.style.TextDirection.Rtl) SkDirection.RTL else SkDirection.LTR
			textStyle = baseStyle
			if (maxLines != Int.MAX_VALUE) {
				maxLinesCount = maxLines
				ellipsis = if (ellipsize) "…" else ""
			}
		}
		// The builder is a native resource; close it once the paragraph is built
		// (the paragraph owns its own native data and outlives the builder).
		val pb = SkParagraphBuilder(pStyle, SkiaFonts.fontCollection)
		try {
			if (spanStyles.isEmpty()) {
				pb.pushStyle(baseStyle)
				pb.addText(text)
				pb.popStyle()
			} else {
				appendWithSpans(pb, color, shadow, decoration)
			}
			return pb.build().also { it.layout(layoutWidth) }
		} finally {
			pb.close()
		}
	}

	override fun dispose() {
		if (disposed) return
		disposed = true
		// All are per-ops native objects (skiko Managed). close() frees them and
		// cancels skiko's own GC-driven Cleaner, so nothing double-frees later.
		paragraph.close()
		defaultFont.close()
		foregroundPaint?.close()
	}

	private fun appendWithSpans(pb: SkParagraphBuilder, color: Color, shadow: Shadow?, decoration: TextDecoration?) {
		val points = buildList {
			add(0); add(text.length)
			spanStyles.forEach { add(it.start.coerceIn(0, text.length)); add(it.end.coerceIn(0, text.length)) }
		}.distinct().sorted()
		for (i in 0 until points.size - 1) {
			val segStart = points[i]
			val segEnd = points[i + 1]
			if (segStart >= segEnd) continue
			val active = spanStyles.filter { it.start <= segStart && it.end >= segEnd }
			pb.pushStyle(segmentStyle(color, shadow, decoration, active))
			pb.addText(text.substring(segStart, segEnd))
			pb.popStyle()
		}
	}

	private fun segmentStyle(
		color: Color, shadow: Shadow?, decoration: TextDecoration?,
		active: List<AnnotatedString.Range<SpanStyle>>,
	): SkTextStyle {
		var family = baseFamily
		var variations = baseVariations
		var size = fontPx
		var segColor = color
		var fontStyle = style.fontStyle
		var deco = decoration
		active.forEach { range ->
			val sp = range.item
			if (sp.color.isSpecified) segColor = sp.color
			sp.fontWeight?.let { variations = listOf(FontVariation.weight(it.weight)) }
			if (sp.fontSize.isSpecified) size = (sp.fontSize.value * density).coerceAtLeast(1f)
			sp.fontStyle?.let { fontStyle = it }
			sp.textDecoration?.let { deco = it }
			sp.fontFamily.projectFontName()?.let { family = it }
			sp.fontFamily.projectFontVariations()?.let { variations = it }
		}
		return makeTextStyle(family, variations, size, segColor, fontStyle, deco, shadow)
	}

	private fun makeTextStyle(
		family: String?, variations: List<FontVariation.Setting>?, sizePx: Float,
		color: Color, fontStyle: FontStyle?, decoration: TextDecoration?, shadow: Shadow?,
	): SkTextStyle {
		val ts = SkTextStyle()
		val argb = (if (color.isSpecified) color else Color.Black).toArgb()
		ts.color = argb
		ts.fontSize = sizePx
		// Match upstream text rasterization (edging / hinting / subpixel) instead
		// of skiko's raw defaults — see the RASTER_* defaults above.
		ts.fontEdging = RASTER_EDGING
		ts.fontHinting = RASTER_HINTING
		ts.subpixel = RASTER_SUBPIXEL
		// Register + resolve to a provider alias so skiko's shaper maps codepoints
		// through the exact typeface (icon fonts would otherwise fall back to
		// Noto Sans and render private-use glyphs as tofu).
		val (tf, alias) = SkiaFonts.resolve(family, variations)
		tf?.let { ts.typeface = it }
		ts.fontFamilies = arrayOf(alias)
		if (fontStyle == FontStyle.Italic) ts.fontStyle = SkFontStyle.ITALIC
		decoration?.takeUnless { it == TextDecoration.None }?.let {
			ts.decorationStyle = SkDecorationStyle(
				it.contains(TextDecoration.Underline), false, it.contains(TextDecoration.LineThrough),
				false, argb, SkDecorationLineStyle.SOLID, 1f,
			)
		}
		val letter = style.letterSpacing
		if (letter.isSpecified) {
			ts.letterSpacing = if (letter.isEm) sizePx * letter.value else letter.value * density
		}
		shadow?.takeUnless { it == Shadow.None }?.let {
			ts.addShadow(SkShadow(it.color.toArgb(), it.offset.x, it.offset.y, it.blurRadius.toDouble()))
		}
		return ts
	}
}

private fun TextAlign.toSkAlignment(): SkAlignment = when (this) {
	TextAlign.Left -> SkAlignment.LEFT
	TextAlign.Right -> SkAlignment.RIGHT
	TextAlign.Center -> SkAlignment.CENTER
	TextAlign.Justify -> SkAlignment.JUSTIFY
	TextAlign.End -> SkAlignment.END
	else -> SkAlignment.START
}

// ==================
// MARK: factory actuals (skikoRendererMain)
// ==================

/** Actual for the nativeMain `expect fun buildParagraphOps`; runs in the skiko
 *  source set (compiled into both the official-skiko and fork siblings). */
internal actual fun buildParagraphOps(
	text: String,
	style: TextStyle,
	width: Float,
	maxLines: Int,
	ellipsize: Boolean,
	density: Float,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
): NativeParagraphOps = SkiaParagraphOps(text, style, width, maxLines, density, spanStyles, ellipsize)

/** Actual for `expect fun paragraphIntrinsicWidths` — [min, max] from an
 *  unbounded layout. */
internal actual fun paragraphIntrinsicWidths(
	text: String,
	style: TextStyle,
	density: Float,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
): FloatArray {
	val ops = SkiaParagraphOps(text, style, Float.POSITIVE_INFINITY, Int.MAX_VALUE, density, spanStyles, false)
	try {
		return floatArrayOf(ops.minIntrinsicWidth, ops.maxIntrinsicWidth)
	} finally {
		ops.dispose()
	}
}
