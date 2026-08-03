package androidx.compose.ui.text

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
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
import org.jetbrains.skia.paragraph.BaselineMode
import org.jetbrains.skia.paragraph.PlaceholderAlignment
import org.jetbrains.skia.paragraph.PlaceholderStyle
import org.jetbrains.skia.paragraph.DecorationLineStyle as SkDecorationLineStyle
import org.jetbrains.skia.paragraph.DecorationStyle as SkDecorationStyle
import org.jetbrains.skia.paragraph.Direction as SkDirection
import org.jetbrains.skia.paragraph.HeightMode as SkHeightMode
import org.jetbrains.skia.paragraph.Paragraph as SkParagraph
import org.jetbrains.skia.paragraph.ParagraphBuilder as SkParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.RectHeightMode
import org.jetbrains.skia.paragraph.RectWidthMode
import org.jetbrains.skia.paragraph.Shadow as SkShadow
import org.jetbrains.skia.paragraph.TextIndent as SkTextIndent
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
	private val placeholders: List<AnnotatedString.Range<Placeholder>> = emptyList(),
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
	// Skia's ParagraphStyle.replaceTabCharacters (set below) expands U+0009 to a space
	// before shaping (CMP-6589). The Windows skiko fork's flat extern-C surface doesn't
	// wire that setter, so a raw tab reaches HarfBuzz, finds no glyph in the font, and
	// renders as a .notdef box. Normalise the SHAPED text here instead — this is exactly
	// what the flag does internally, so it's upstream-faithful and platform-independent.
	// It's length-preserving, so every offset query (getRectsForRange / getCursorRect /
	// wordBoundary / lineMetrics / span cut points) keeps operating on the original [text].
	private val shapedText: String =
		if (text.indexOf('\t') >= 0) text.replace('\t', ' ') else text
	// var so relayout() (intrinsics-reuse fast path) can re-break at the final
	// width and keep rebuildAndPaint()'s re-layout consistent with it.
	private var layoutWidth: Float =
		if (widthConstraint.isFinite() && widthConstraint > 0f) widthConstraint else INTRINSIC_WIDTH

	/** Resolve the paragraph line height to px for a run of [runSizePx], mirroring
	   upstream's `lineHeight.toPx(density, fontSize)`: `em` is relative to the run's
	   font size, `sp` scales by density. Null when unspecified (keep skiko default
	   line spacing — the pre-existing behaviour, so untouched text doesn't shift). */
	private fun resolveLineHeightPx(runSizePx: Float): Float? {
		val lh = style.lineHeight
		if (!lh.isSpecified) return null
		return when {
			lh.isEm -> runSizePx * lh.value
			lh.isSp -> lh.value * density
			else -> null
		}
	}

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
		val baseStyle = makeTextStyle(
			baseFamily, baseVariations, fontPx, color, style.fontStyle, decoration, shadow,
			style.baselineShift, style.background,
		)
		val pStyle = ParagraphStyle().apply {
			// https://youtrack.jetbrains.com/issue/CMP-6589 — tabs expand like upstream.
			replaceTabCharacters = true
			alignment = style.textAlign.toSkAlignment()
			direction = if (style.textDirection == androidx.compose.ui.text.style.TextDirection.Rtl) SkDirection.RTL else SkDirection.LTR
			textStyle = baseStyle
			// Line-height trim (mirrors upstream ParagraphBuilder.textStyleToParagraphStyle):
			// trim-based mode only when lineHeight actually adds leading (> fontSize);
			// otherwise DISABLE_ALL, matching upstream's default (with no extra leading
			// this leaves single-line metrics at the font's own ascent/descent).
			val baseLineHeightPx = resolveLineHeightPx(fontPx)
			heightMode = if (baseLineHeightPx != null && baseLineHeightPx > fontPx) {
				(style.lineHeightStyle ?: LineHeightStyle.Default).trim.toSkHeightMode()
			} else {
				SkHeightMode.DISABLE_ALL
			}
			style.textIndent?.let { ti ->
				val em = fontPx
				fun tuPx(v: androidx.compose.ui.unit.TextUnit): Float = when {
					!v.isSpecified -> 0f
					v.isEm -> em * v.value
					else -> v.value * density
				}
				textIndent = SkTextIndent(tuPx(ti.firstLine), tuPx(ti.restLine))
			}
			if (maxLines != Int.MAX_VALUE) {
				maxLinesCount = maxLines
				ellipsis = if (ellipsize) "…" else ""
			}
		}
		// The builder is a native resource; close it once the paragraph is built
		// (the paragraph owns its own native data and outlives the builder).
		val pb = SkParagraphBuilder(pStyle, SkiaFonts.fontCollection)
		try {
			if (spanStyles.isEmpty() && placeholders.isEmpty()) {
				pb.pushStyle(baseStyle)
				pb.addText(shapedText)
				pb.popStyle()
			} else {
				appendWithSpans(pb, color, shadow, decoration)
			}
			return pb.build().also { it.layout(layoutWidth) }
		} finally {
			pb.close()
		}
	}

	override fun relayout(width: Float) {
		val newWidth = if (width.isFinite() && width > 0f) width else INTRINSIC_WIDTH
		if (newWidth == layoutWidth) return
		layoutWidth = newWidth
		paragraph.layout(newWidth)
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
		val len = text.length
		val points = buildList {
			add(0); add(len)
			spanStyles.forEach { add(it.start.coerceIn(0, len)); add(it.end.coerceIn(0, len)) }
			placeholders.forEach { add(it.start.coerceIn(0, len)); add(it.end.coerceIn(0, len)) }
		}.distinct().sorted()
		for (i in 0 until points.size - 1) {
			val segStart = points[i]
			val segEnd = points[i + 1]
			if (segStart >= segEnd) continue
			// A placeholder replaces its text range with a single reserved box:
			// emit it once at its start and skip the covered text (upstream
			// ParagraphBuilder.makeOps / addPlaceholder). Cut points include every
			// placeholder boundary, so a segment is fully inside or fully outside.
			val ph = placeholders.firstOrNull { it.start <= segStart && it.end > segStart }
			if (ph != null) {
				if (ph.start == segStart) pb.addPlaceholder(placeholderStyle(ph.item, segStart))
				continue
			}
			val active = spanStyles.filter { it.start <= segStart && it.end >= segEnd }
			pb.pushStyle(segmentStyle(color, shadow, decoration, active))
			pb.addText(shapedText.substring(segStart, segEnd))
			pb.popStyle()
		}
	}

	/** Map a Compose [Placeholder] to a skiko PlaceholderStyle. Width/height are
	   resolved against the font size active at [atOffset] (em is relative to it),
	   mirroring upstream ParagraphBuilder. */
	private fun placeholderStyle(p: Placeholder, atOffset: Int): PlaceholderStyle {
		val runFontPx = spanStyles
			.filter { it.start <= atOffset && it.end > atOffset && it.item.fontSize.isSpecified }
			.lastOrNull()?.item?.fontSize?.let { (it.value * density).coerceAtLeast(1f) } ?: fontPx
		fun tuPx(v: androidx.compose.ui.unit.TextUnit): Float = when {
			!v.isSpecified -> runFontPx
			v.isEm -> runFontPx * v.value
			else -> v.value * density
		}
		return PlaceholderStyle(
			tuPx(p.width), tuPx(p.height),
			p.placeholderVerticalAlign.toSkPlaceholderAlignment(),
			BaselineMode.ALPHABETIC, 0f,
		)
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
		var baselineShift = style.baselineShift
		var background = style.background
		active.forEach { range ->
			val sp = range.item
			if (sp.color.isSpecified) segColor = sp.color
			sp.fontWeight?.let { variations = listOf(FontVariation.weight(it.weight)) }
			if (sp.fontSize.isSpecified) size = (sp.fontSize.value * density).coerceAtLeast(1f)
			sp.fontStyle?.let { fontStyle = it }
			sp.textDecoration?.let { deco = it }
			sp.fontFamily.projectFontName()?.let { family = it }
			sp.fontFamily.projectFontVariations()?.let { variations = it }
			sp.baselineShift?.let { baselineShift = it }
			if (sp.background.isSpecified) background = sp.background
		}
		return makeTextStyle(family, variations, size, segColor, fontStyle, deco, shadow, baselineShift, background)
	}

	private fun makeTextStyle(
		family: String?, variations: List<FontVariation.Setting>?, sizePx: Float,
		color: Color, fontStyle: FontStyle?, decoration: TextDecoration?, shadow: Shadow?,
		baselineShift: BaselineShift?, background: Color,
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
		// Per-run line height (upstream: res.height = lineHeight / fontSize), set only
		// when lineHeight is specified so untouched text keeps skiko's default spacing.
		resolveLineHeightPx(sizePx)?.let { ts.height = it / sizePx }
		// Span/base background fill behind the run.
		if (background.isSpecified) ts.background = SkPaint().also { it.color = background.toArgb() }
		// Register + resolve to a provider alias so skiko's shaper maps codepoints
		// through the exact typeface (icon fonts would otherwise fall back to
		// Noto Sans and render private-use glyphs as tofu).
		val (tf, alias) = SkiaFonts.resolve(family, variations)
		tf?.let { ts.typeface = it }
		ts.fontFamilies = arrayOf(alias)
		// Superscript / subscript: shift the baseline by a multiple of the font ascent.
		// MUST run after the typeface + fontSize are set — `fontMetrics` is undefined
		// without a resolved font, and skiko's setBaselineShift rejects the NaN.
		baselineShift?.let { ts.baselineShift = it.multiplier * ts.fontMetrics.ascent }
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

private fun PlaceholderVerticalAlign.toSkPlaceholderAlignment(): PlaceholderAlignment = when (this) {
	PlaceholderVerticalAlign.AboveBaseline -> PlaceholderAlignment.ABOVE_BASELINE
	PlaceholderVerticalAlign.TextTop -> PlaceholderAlignment.TOP
	PlaceholderVerticalAlign.TextBottom -> PlaceholderAlignment.BOTTOM
	PlaceholderVerticalAlign.TextCenter -> PlaceholderAlignment.MIDDLE
	PlaceholderVerticalAlign.Top -> PlaceholderAlignment.TOP
	PlaceholderVerticalAlign.Bottom -> PlaceholderAlignment.BOTTOM
	PlaceholderVerticalAlign.Center -> PlaceholderAlignment.MIDDLE
	else -> PlaceholderAlignment.ABOVE_BASELINE
}

private fun LineHeightStyle.Trim.toSkHeightMode(): SkHeightMode = when (this) {
	LineHeightStyle.Trim.Both -> SkHeightMode.DISABLE_ALL
	LineHeightStyle.Trim.FirstLineTop -> SkHeightMode.DISABLE_FIRST_ASCENT
	LineHeightStyle.Trim.LastLineBottom -> SkHeightMode.DISABLE_LAST_DESCENT
	LineHeightStyle.Trim.None -> SkHeightMode.ALL
	else -> SkHeightMode.DISABLE_ALL
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
	placeholders: List<AnnotatedString.Range<Placeholder>>,
): NativeParagraphOps = SkiaParagraphOps(text, style, width, maxLines, density, spanStyles, ellipsize, placeholders)

