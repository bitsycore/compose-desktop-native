package androidx.compose.ui.text

import com.compose.desktop.native.text.projectFontName
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isUnspecified
import com.compose.desktop.native.text.NativeTextCanvas
import com.compose.desktop.native.text.currentTextMeasurer
import kotlin.math.max

// ==================
// MARK: SdlParagraph — Paragraph actual bridged to the project TextMeasurer
// ==================

/*
 The upstream `Paragraph` (Skia/skiko-only) can't be used on the SDL target, so this bridges the
 upstream Paragraph interface to the project's name-based `TextMeasurer` (SDL_ttf / FreeType line
 wrapping + measurement). MEASUREMENT is real (width/height/line metrics/offset<->position/cursor/
 word boundary); PAINT is stubbed for now (Phase 1) — MultiParagraph draws through the existing
 project text path, so this actual is exercised for LAYOUT geometry, not glyph rasterisation yet.

 fontSize is `sp` and is converted to physical pixels through `density`. `Modifier.padding` /
 `defaultMinSize` / etc. also resolve `Dp.toPx()` via `LocalDensity` in the tree; this file matches
 that convention so text measurement lands in the same pixel space as everything else.
*/
internal class SdlParagraph(
	private val text: String,
	private val style: TextStyle,
	widthConstraint: Float,
	private val maxLines: Int,
	density: Float = 1f,
	// AnnotatedString span ranges (colour, weight, family, size). Ignored for
	// measurement — the wrap path measures in the base style's font metrics — but
	// forwarded to the native paint path so each glyph run picks up its span's
	// colour / weight / italic / family / fontSize when drawn. Weight + italic
	// currently only reach the Skia renderer path (SkiaTextRenderer.drawText),
	// which is where the multi-run colour + font pipeline is already wired.
	private val spanStyles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
) : Paragraph {

	private val fontPx: Int =
		((if (style.fontSize.isUnspecified) 14f else style.fontSize.value) * density).toInt().coerceAtLeast(1)
	private val family: String? = style.fontFamily.projectFontName()

	private val maxWidthPx: Int =
		if (widthConstraint.isFinite() && widthConstraint > 0f) widthConstraint.toInt() else Int.MAX_VALUE

	// The paragraph's fontWeight mapped onto the variable font's `wght` axis.
	// Threaded through BOTH measurement and paint so the layout box matches the
	// weighted glyphs — otherwise heavier text is measured at Normal width and
	// the last character clips (e.g. "Search" → "Searc").
	private val variations: List<androidx.compose.ui.text.font.FontVariation.Setting>? =
		style.fontWeight?.let { listOf(androidx.compose.ui.text.font.FontVariation.weight(it.weight)) }

	private val wrapped = currentTextMeasurer.wrap(text, fontPx, maxWidthPx, family, variations)
	private val allLines: List<String> = wrapped.lines
	private val lineStarts: IntArray = wrapped.lineStarts
	private val lh: Float = currentTextMeasurer.lineHeight(fontPx, family, variations).coerceAtLeast(1f)

	override val lineCount: Int = minOf(allLines.size, maxLines).coerceAtLeast(1)
	override val didExceedMaxLines: Boolean = allLines.size > maxLines

	private fun measureStr(inStr: String): Float =
		currentTextMeasurer.measure(inStr, fontPx, Int.MAX_VALUE, family, variations).width.toFloat()

	// Lazy — measuring every wrapped line up-front taxed paragraphs that are
	// only ever painted (draw doesn't read per-line widths; alignment and
	// selection do, on demand).
	private val lineWidths: FloatArray by lazy { FloatArray(lineCount) { measureStr(allLines[it]) } }

	override val width: Float by lazy {
		if (maxWidthPx == Int.MAX_VALUE) (lineWidths.maxOrNull() ?: 0f) else widthConstraint
	}
	override val height: Float = lineCount * lh
	// Widest single HARD-BREAK line — NOT the concatenated all-on-one-line width.
	// Compose's Text(softWrap = false) reads this to decide the paragraph width
	// (LayoutUtils.finalMaxWidth returns Constraints.Infinity when softWrap=false,
	// then clamps maxIntrinsicWidth into [minWidth, Infinity]). Measuring the whole
	// text `.replace("\n", " ")` as one line reported a 17-line gutter as a ~600px
	// single line, so a Modifier.width(18.dp) gutter Text got laid out at 600px
	// wide and the line-number column vanished under the body text.
	// Both intrinsics are LAZY — they measure every hard line / every word, and
	// most paragraphs (bounded-width Text) never read them. Eager computation
	// here used to tax every Text() layout with a full extra measurement pass.
	override val maxIntrinsicWidth: Float by lazy {
		var vMax = 0f
		var vStart = 0
		while (vStart <= text.length) {
			val vNl = text.indexOf('\n', vStart)
			val vEnd = if (vNl < 0) text.length else vNl
			val vLine = text.substring(vStart, vEnd)
			val vW = if (vLine.isEmpty()) 0f else measureStr(vLine)
			if (vW > vMax) vMax = vW
			if (vNl < 0) break
			vStart = vNl + 1
		}
		vMax
	}
	// Manual whitespace scan — the old Regex("\\s+").split compiled the regex
	// and allocated the full word list on every paragraph construction.
	override val minIntrinsicWidth: Float by lazy {
		var vMax = 0f
		var vI = 0
		val vN = text.length
		while (vI < vN) {
			while (vI < vN && text[vI].isWhitespace()) vI++
			val vStart = vI
			while (vI < vN && !text[vI].isWhitespace()) vI++
			if (vI > vStart) vMax = max(vMax, measureStr(text.substring(vStart, vI)))
		}
		vMax
	}

	private val ascent: Float = lh * 0.8f
	override val firstBaseline: Float = ascent
	override val lastBaseline: Float = (lineCount - 1) * lh + ascent

	override val placeholderRects: List<Rect?> = emptyList()

	// ============
	//  Line metrics
	override fun getLineLeft(lineIndex: Int): Float = 0f
	override fun getLineRight(lineIndex: Int): Float = lineWidths.getOrElse(lineIndex) { 0f }
	override fun getLineWidth(lineIndex: Int): Float = lineWidths.getOrElse(lineIndex) { 0f }
	override fun getLineTop(lineIndex: Int): Float = lineIndex * lh
	override fun getLineBottom(lineIndex: Int): Float = (lineIndex + 1) * lh
	override fun getLineHeight(lineIndex: Int): Float = lh
	override fun getLineBaseline(lineIndex: Int): Float = lineIndex * lh + ascent
	override fun getLineStart(lineIndex: Int): Int = lineStarts.getOrElse(lineIndex) { 0 }
	override fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int =
		lineStarts.getOrElse(lineIndex) { 0 } + allLines.getOrElse(lineIndex) { "" }.length
	override fun isLineEllipsized(lineIndex: Int): Boolean = false

	override fun getLineForOffset(offset: Int): Int {
		var vLine = 0
		for (i in 0 until lineCount) {
			if (lineStarts[i] <= offset) vLine = i else break
		}
		return vLine
	}

	override fun getLineForVerticalPosition(vertical: Float): Int =
		(vertical / lh).toInt().coerceIn(0, lineCount - 1)

	// ============
	//  Offset <-> position

	// Per-line cumulative advances, built lazily on the first position query:
	// advances[c] = rendered width of the line's first c chars. Cursor math and
	// selection then read O(1) / binary-search instead of re-measuring substring
	// prefixes on every call (the old path was O(n²) per pointer event).
	private val lineAdvances = arrayOfNulls<FloatArray>(lineCount)

	private fun advancesFor(inLine: Int): FloatArray {
		lineAdvances.getOrNull(inLine)?.let { if (it != null) return it }
		val vLineStr = allLines.getOrElse(inLine) { "" }
		val vArr = FloatArray(vLineStr.length + 1)
		for (c in 1..vLineStr.length) vArr[c] = measureStr(vLineStr.substring(0, c))
		if (inLine in lineAdvances.indices) lineAdvances[inLine] = vArr
		return vArr
	}

	override fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float {
		val vLine = getLineForOffset(offset)
		val vAdv = advancesFor(vLine)
		val vCol = (offset - lineStarts[vLine]).coerceIn(0, vAdv.size - 1)
		return vAdv[vCol]
	}

	override fun getOffsetForPosition(position: Offset): Int {
		val vLine = (position.y / lh).toInt().coerceIn(0, lineCount - 1)
		val vAdv = advancesFor(vLine)
		// Advances are monotonic — binary-search the first edge >= x, then pick
		// the nearer of it and its left neighbour (same "nearest left edge"
		// semantics as the old linear scan).
		var vLo = 0
		var vHi = vAdv.size - 1
		while (vLo < vHi) {
			val vMid = (vLo + vHi) / 2
			if (vAdv[vMid] < position.x) vLo = vMid + 1 else vHi = vMid
		}
		val vBest = if (vLo > 0 && (position.x - vAdv[vLo - 1]) <= (vAdv[vLo] - position.x)) vLo - 1 else vLo
		return lineStarts[vLine] + vBest
	}

	override fun getCursorRect(offset: Int): Rect {
		val vLine = getLineForOffset(offset)
		val vX = getHorizontalPosition(offset, true)
		return Rect(vX, getLineTop(vLine), vX + 1f, getLineBottom(vLine))
	}

	override fun getBoundingBox(offset: Int): Rect {
		val vLine = getLineForOffset(offset)
		val vX0 = getHorizontalPosition(offset, true)
		val vX1 = getHorizontalPosition(offset + 1, true)
		return Rect(vX0, getLineTop(vLine), max(vX1, vX0), getLineBottom(vLine))
	}

	override fun fillBoundingBoxes(range: TextRange, array: FloatArray, arrayStart: Int) {
		var vI = arrayStart
		for (o in range.min until range.max) {
			val vBox = getBoundingBox(o)
			if (vI + 3 < array.size) {
				array[vI] = vBox.left; array[vI + 1] = vBox.top
				array[vI + 2] = vBox.right; array[vI + 3] = vBox.bottom
			}
			vI += 4
		}
	}

	override fun getWordBoundary(offset: Int): TextRange {
		if (text.isEmpty()) return TextRange.Zero
		val vAt = offset.coerceIn(0, text.length - 1)
		if (text[vAt].isWhitespace()) return TextRange(offset, offset)
		var vStart = vAt
		while (vStart > 0 && !text[vStart - 1].isWhitespace()) vStart--
		var vEnd = vAt
		while (vEnd < text.length && !text[vEnd].isWhitespace()) vEnd++
		return TextRange(vStart, vEnd)
	}

	override fun getParagraphDirection(offset: Int): ResolvedTextDirection = ResolvedTextDirection.Ltr
	override fun getBidiRunDirection(offset: Int): ResolvedTextDirection = ResolvedTextDirection.Ltr

	override fun getPathForRange(start: Int, end: Int): Path {
		// Selection highlight is rendered by TextFieldDelegate.draw via
		// `textLayoutResult.getPathForRange(start, end)` + `canvas.drawPath(path, paint)`.
		// Empty path here meant Shift+arrow updated the value's selection state, but the
		// blue selection band never painted. Build a rectangle per line in the range,
		// using the same wrap output the paint path already uses so bounds stay aligned
		// to what's on screen.
		val vPath = Path()
		if (start >= end || text.isEmpty()) return vPath
		val vFrom = start.coerceIn(0, text.length)
		val vTo = end.coerceIn(0, text.length)
		if (vFrom == vTo) return vPath
		val vFirstLine = getLineForOffset(vFrom)
		val vLastLine = getLineForOffset(vTo).coerceAtMost(lineCount - 1)
		for (vLine in vFirstLine..vLastLine) {
			val vLineStart = lineStarts[vLine]
			val vLineEnd = vLineStart + (allLines.getOrElse(vLine) { "" }.length)
			val vRangeStart = maxOf(vFrom, vLineStart)
			val vRangeEnd = minOf(vTo, vLineEnd)
			val vLeftPx = getHorizontalPosition(vRangeStart, true)
			val vRightPx =
				if (vRangeEnd < vLineEnd || vLine == vLastLine) getHorizontalPosition(vRangeEnd, true)
				else lineWidths[vLine] // selection extends past newline → paint to line's end
			if (vRightPx > vLeftPx) {
				vPath.addRect(Rect(vLeftPx, getLineTop(vLine), vRightPx, getLineBottom(vLine)))
			}
		}
		return vPath
	}

	override fun getRangeForRect(
		rect: Rect,
		granularity: TextGranularity,
		inclusionStrategy: TextInclusionStrategy,
	): TextRange = TextRange.Zero

	// ============
	//  Paint — Phase 2. Route through NativeTextCanvas.drawNativeText using the
	//  paragraph's already-computed wrap so no re-measure happens at draw time.
	//  Shadow / textDecoration / drawStyle / blendMode aren't wired yet (SDL text
	//  path draws opaque glyphs; those are accept-and-ignore).

	private fun paintCore(
		inCanvas: Canvas,
		inColor: Color,
		@Suppress("UNUSED_PARAMETER") inShadow: Shadow?,
		@Suppress("UNUSED_PARAMETER") inDecoration: TextDecoration?,
		@Suppress("UNUSED_PARAMETER") inDrawStyle: DrawStyle?,
		@Suppress("UNUSED_PARAMETER") inBlendMode: BlendMode,
	) {
		val vNative = inCanvas as? NativeTextCanvas ?: return
		val vEffectiveColor = if (inColor == Color.Unspecified) (style.color.takeIf { it != Color.Unspecified } ?: Color.Black) else inColor
		val vAlign = style.textAlign ?: TextAlign.Start
		vNative.drawNativeText(
			inText = text,
			inSpans = spanStyles.takeIf { it.isNotEmpty() },
			inX = 0f,
			inY = 0f,
			inBoxWidth = width,
			inBoxHeight = height,
			inColor = vEffectiveColor,
			inFontSizePx = fontPx,
			inTextAlign = vAlign,
			inSoftWrap = maxWidthPx != Int.MAX_VALUE,
			inFontFamily = family,
			inFontVariations = variations,
		)
	}

	@Deprecated("Use the new paint function that takes canvas as the only required parameter.", level = DeprecationLevel.HIDDEN)
	override fun paint(canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?) {
		paintCore(canvas, color, shadow, textDecoration, null, BlendMode.SrcOver)
	}

	override fun paint(
		canvas: Canvas,
		color: Color,
		shadow: Shadow?,
		textDecoration: TextDecoration?,
		drawStyle: DrawStyle?,
		blendMode: BlendMode,
	) {
		paintCore(canvas, color, shadow, textDecoration, drawStyle, blendMode)
	}

	override fun paint(
		canvas: Canvas,
		brush: Brush,
		alpha: Float,
		shadow: Shadow?,
		textDecoration: TextDecoration?,
		drawStyle: DrawStyle?,
		blendMode: BlendMode,
	) {
		// Reduce brush to the SolidColor case (SDL text path is solid-color only);
		// non-SolidColor brushes fall back to the style's color.
		val vColor = (brush as? SolidColor)?.value?.let {
			if (alpha.isFinite()) it.copy(alpha = it.alpha * alpha) else it
		} ?: style.color
		paintCore(canvas, vColor, shadow, textDecoration, drawStyle, blendMode)
	}
}
