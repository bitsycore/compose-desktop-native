package androidx.compose.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isUnspecified
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

 fontSize is treated as pixels (matching the project's `fontSize.value.toInt()` convention — the SDL
 renderer scales by DPR at draw time), so `density` is accepted-and-ignored here.
*/
internal class SdlParagraph(
	private val text: String,
	private val style: TextStyle,
	widthConstraint: Float,
	private val maxLines: Int,
) : Paragraph {

	private val fontPx: Int =
		(if (style.fontSize.isUnspecified) 14f else style.fontSize.value).toInt().coerceAtLeast(1)
	private val family: String? = (style.fontFamily as? FontFamily.Named)?.name

	private val maxWidthPx: Int =
		if (widthConstraint.isFinite() && widthConstraint > 0f) widthConstraint.toInt() else Int.MAX_VALUE

	private val wrapped = currentTextMeasurer.wrap(text, fontPx, maxWidthPx, family)
	private val allLines: List<String> = wrapped.lines
	private val lineStarts: IntArray = wrapped.lineStarts
	private val lh: Float = currentTextMeasurer.lineHeight(fontPx, family).coerceAtLeast(1f)

	override val lineCount: Int = minOf(allLines.size, maxLines).coerceAtLeast(1)
	override val didExceedMaxLines: Boolean = allLines.size > maxLines

	private fun measureStr(inStr: String): Float =
		currentTextMeasurer.measure(inStr, fontPx, Int.MAX_VALUE, family).width.toFloat()

	private val lineWidths: FloatArray = FloatArray(lineCount) { measureStr(allLines[it]) }

	override val width: Float =
		if (maxWidthPx == Int.MAX_VALUE) (lineWidths.maxOrNull() ?: 0f) else widthConstraint
	override val height: Float = lineCount * lh
	override val maxIntrinsicWidth: Float = measureStr(text.replace("\n", " "))
	override val minIntrinsicWidth: Float =
		text.split(Regex("\\s+")).fold(0f) { acc, w -> max(acc, measureStr(w)) }

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
	override fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float {
		val vLine = getLineForOffset(offset)
		val vLineStr = allLines.getOrElse(vLine) { "" }
		val vCol = (offset - lineStarts[vLine]).coerceIn(0, vLineStr.length)
		return measureStr(vLineStr.substring(0, vCol))
	}

	override fun getOffsetForPosition(position: Offset): Int {
		val vLine = (position.y / lh).toInt().coerceIn(0, lineCount - 1)
		val vLineStr = allLines.getOrElse(vLine) { "" }
		// Find the column whose left edge is nearest position.x.
		var vBest = 0
		var vBestDx = Float.MAX_VALUE
		for (c in 0..vLineStr.length) {
			val vX = measureStr(vLineStr.substring(0, c))
			val vDx = kotlin.math.abs(vX - position.x)
			if (vDx < vBestDx) { vBestDx = vDx; vBest = c }
		}
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

	override fun getPathForRange(start: Int, end: Int): Path = Path()

	override fun getRangeForRect(
		rect: Rect,
		granularity: TextGranularity,
		inclusionStrategy: TextInclusionStrategy,
	): TextRange = TextRange.Zero

	// ============
	//  Paint — Phase 1 no-op (MultiParagraph still paints via the project text path).
	@Deprecated("Use the new paint function that takes canvas as the only required parameter.", level = DeprecationLevel.HIDDEN)
	override fun paint(canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?) {}

	override fun paint(
		canvas: Canvas,
		color: Color,
		shadow: Shadow?,
		textDecoration: TextDecoration?,
		drawStyle: DrawStyle?,
		blendMode: BlendMode,
	) {}

	override fun paint(
		canvas: Canvas,
		brush: Brush,
		alpha: Float,
		shadow: Shadow?,
		textDecoration: TextDecoration?,
		drawStyle: DrawStyle?,
		blendMode: BlendMode,
	) {}
}
