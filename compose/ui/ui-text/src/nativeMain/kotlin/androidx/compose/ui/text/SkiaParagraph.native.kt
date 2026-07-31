package androidx.compose.ui.text

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
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.floor

// ==================
// MARK: SkiaParagraph — nativeMain Paragraph over a skiko-backed ops seam
// ==================
//
// `Paragraph` is a sealed interface whose actual is declared in nativeMain, so
// its implementers must live here (skiko-tied code in the child skikoRenderer
// source set counts as a "different module" for the sealed check). This class
// therefore holds no skiko types: it drives the real skiko skparagraph through
// [NativeParagraphOps] (impl in skikoRendererMain), exchanging plain data
// ([TextBoxData] / [LineMetricData] / [RectData]). Cursor/selection/line-metric
// math is adapted from upstream `SkiaParagraph.skiko.kt` (@ CMP core v1.12.x).

/** Plain mirror of a skiko `TextBox` (a glyph-run rect + its direction). */
internal class TextBoxData(
	val left: Float, val top: Float, val right: Float, val bottom: Float, val isRtl: Boolean,
)

/** Plain mirror of a skiko paragraph `LineMetrics`. */
internal class LineMetricData(
	val startIndex: Int,
	val endIndex: Int,
	val endExcludingWhitespaces: Int,
	val endIncludingNewline: Int,
	val isHardBreak: Boolean,
	val ascent: Double,
	val descent: Double,
	val baseline: Double,
	val left: Double,
	val right: Double,
	val width: Double,
	val height: Double,
	val lineNumber: Int,
)

/** Plain mirror of a Compose/skiko rect. */
internal class RectData(val left: Float, val top: Float, val right: Float, val bottom: Float) {
	fun toComposeRect() = Rect(left, top, right, bottom)
}

/**
 * Skiko-free view of one laid-out skiko paragraph. Implemented in
 * skikoRendererMain (SkiaParagraphOps) where skiko is on the classpath; all
 * exchange types are plain so this interface can live in nativeMain beside the
 * sealed [Paragraph]. [rebuildAndPaint] re-lays-out with paint-time overrides
 * (Compose paints text with the resolved colour/shadow/decoration) then draws.
 */
internal interface NativeParagraphOps {
	val height: Float
	val lineNumber: Int
	val minIntrinsicWidth: Float
	val maxIntrinsicWidth: Float
	val didExceedMaxLines: Boolean
	val alphabeticBaseline: Float
	val defaultAscentPx: Float // positive: -fontMetrics.ascent
	val defaultDescentPx: Float

	fun lineMetrics(): List<LineMetricData>
	fun placeholderRects(): List<RectData?>
	fun getRectsForRange(start: Int, end: Int, useMaxHeight: Boolean): List<TextBoxData>
	fun glyphPositionAtCoordinate(x: Float, y: Float): Int
	fun wordBoundary(offset: Int): IntArray // [start, end]

	fun rebuildAndPaint(canvas: Canvas, color: Color, shadow: Shadow?, decoration: TextDecoration?)

	/** Explicitly free the underlying native paragraph. Call when the ops is
	   discarded (e.g. an intrinsics-only throwaway) so the native memory is
	   released deterministically instead of waiting for a GC-driven Cleaner. */
	fun dispose()
}

/** Bridge to the skiko ops impl (actual in skikoRendererMain). */
internal expect fun buildParagraphOps(
	text: String,
	style: TextStyle,
	width: Float,
	maxLines: Int,
	ellipsize: Boolean,
	density: Float,
	spanStyles: List<AnnotatedString.Range<SpanStyle>>,
	placeholders: List<AnnotatedString.Range<Placeholder>>,
): NativeParagraphOps

internal class SkiaParagraph(
	private val text: String,
	private val style: TextStyle,
	private val widthConstraint: Float,
	private val ops: NativeParagraphOps,
) : Paragraph {

	private val textDirection: ResolvedTextDirection = ResolvedTextDirection.Ltr

	override val width: Float
		get() = if (widthConstraint.isFinite()) widthConstraint else ops.maxIntrinsicWidth
	override val height: Float get() = ops.height
	override val minIntrinsicWidth: Float get() = ops.minIntrinsicWidth
	override val maxIntrinsicWidth: Float get() = ops.maxIntrinsicWidth
	override val firstBaseline: Float get() = lineMetrics.firstOrNull()?.baseline?.toFloat() ?: 0f
	override val lastBaseline: Float get() = lineMetrics.lastOrNull()?.baseline?.toFloat() ?: 0f
	override val didExceedMaxLines: Boolean get() = ops.didExceedMaxLines

	override val lineCount: Int
		get() = if (text == "" || ops.lineNumber < 1) 1 else ops.lineNumber

	override val placeholderRects: List<Rect?>
		get() = ops.placeholderRects().map { it?.toComposeRect() }

	override fun getPathForRange(start: Int, end: Int): Path {
		val path = Path()
		for (b in ops.getRectsForRange(start, end, useMaxHeight = true)) {
			path.addRect(Rect(b.left, b.top, b.right, b.bottom))
		}
		return path
	}

	override fun getCursorRect(offset: Int): Rect {
		val horizontal = getHorizontalPosition(offset, true)
		val line = lineMetricsForOffset(offset) ?: return Rect(horizontal, 0f, horizontal, ops.defaultDescentPx)
		val isNewEmptyLine = offset - 1 == line.startIndex && offset == text.length
		val asc = line.ascent.let { if (isNewEmptyLine) it.coerceAtMost(ops.defaultAscentPx.toDouble()) else it }
		val desc = line.descent.let { if (isNewEmptyLine) it.coerceAtMost(ops.defaultDescentPx.toDouble()) else it }
		return Rect(horizontal, (line.baseline - asc).toFloat(), horizontal, (line.baseline + desc).toFloat())
	}

	override fun getLineLeft(lineIndex: Int): Float = lineMetrics.getOrNull(lineIndex)?.left?.toFloat() ?: 0f
	override fun getLineRight(lineIndex: Int): Float = lineMetrics.getOrNull(lineIndex)?.right?.toFloat() ?: 0f
	override fun getLineTop(lineIndex: Int): Float =
		lineMetrics.getOrNull(lineIndex)?.let { floor((it.baseline - it.ascent).toFloat()) } ?: 0f
	override fun getLineBottom(lineIndex: Int): Float =
		lineMetrics.getOrNull(lineIndex)?.let { floor((it.baseline + it.descent).toFloat()) } ?: 0f
	override fun getLineBaseline(lineIndex: Int): Float = lineMetrics.getOrNull(lineIndex)?.baseline?.toFloat() ?: 0f
	override fun getLineHeight(lineIndex: Int): Float = lineMetrics.getOrNull(lineIndex)?.height?.toFloat() ?: 0f
	override fun getLineWidth(lineIndex: Int): Float = lineMetrics.getOrNull(lineIndex)?.width?.toFloat() ?: 0f
	override fun getLineStart(lineIndex: Int): Int = lineMetrics.getOrNull(lineIndex)?.startIndex ?: 0

	override fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int {
		val metrics = lineMetrics.getOrNull(lineIndex) ?: return 0
		return if (visibleEnd) {
			if (lineIndex > 0 && metrics.startIndex < lineMetrics[lineIndex - 1].endIndex) metrics.endIndex
			else if (metrics.startIndex < text.length && text[metrics.startIndex] == '\n') metrics.startIndex
			else metrics.endExcludingWhitespaces
		} else metrics.endIndex
	}

	override fun isLineEllipsized(lineIndex: Int): Boolean = false

	override fun getLineForOffset(offset: Int): Int = when {
		offset < 0 -> 0
		offset > text.length -> lineCount - 1
		else -> lineMetricsForOffset(offset)?.lineNumber ?: 0
	}

	override fun getLineForVerticalPosition(vertical: Float): Int =
		lineMetricsForVerticalPosition(vertical)?.lineNumber ?: 0

	override fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float {
		val prevBox = getBoxBackwardByOffset(offset)
		val nextBox = getBoxForwardByOffset(offset)
		val isRtl = textDirection == ResolvedTextDirection.Rtl
		return when {
			prevBox == null && nextBox == null -> getAlignedStartingPosition(isRtl)
			prevBox == null -> nextBox!!.cursorHorizontalPosition(true)
			nextBox == null -> prevBox.cursorHorizontalPosition()
			nextBox.isRtl == prevBox.isRtl -> nextBox.cursorHorizontalPosition(true)
			!isRtl && !prevBox.isRtl -> nextBox.cursorHorizontalPosition(opposite = true)
			isRtl && prevBox.isRtl -> nextBox.cursorHorizontalPosition(opposite = true)
			usePrimaryDirection -> prevBox.cursorHorizontalPosition()
			else -> nextBox.cursorHorizontalPosition(true)
		}
	}

	private fun getAlignedStartingPosition(isRtl: Boolean): Float = when (style.textAlign) {
		TextAlign.Left -> 0f
		TextAlign.Right -> width
		TextAlign.Center -> width / 2
		TextAlign.Start -> if (isRtl) width else 0f
		TextAlign.End -> if (isRtl) 0f else width
		else -> 0f
	}

	override fun getParagraphDirection(offset: Int): ResolvedTextDirection = textDirection

	override fun getBidiRunDirection(offset: Int): ResolvedTextDirection =
		if (getBoxForwardByOffset(offset)?.isRtl == true) ResolvedTextDirection.Rtl else ResolvedTextDirection.Ltr

	override fun getOffsetForPosition(position: Offset): Int {
		val glyphPosition = ops.glyphPositionAtCoordinate(position.x, position.y)
		val expectedLine = lineMetricsForVerticalPosition(position.y) ?: return glyphPosition
		if (position.x > expectedLine.left && position.x < expectedLine.right) return glyphPosition
		val isNotEmptyLine = expectedLine.startIndex < expectedLine.endIndex
		val rects = if (isNotEmptyLine) ops.getRectsForRange(
			expectedLine.startIndex,
			if (expectedLine.isHardBreak) expectedLine.endIndex else expectedLine.endIndex - 1,
			useMaxHeight = false,
		) else null
		val leftX = rects?.firstOrNull()?.left ?: expectedLine.left.toFloat()
		val rightX = rects?.lastOrNull()?.right ?: expectedLine.right.toFloat()
		if (leftX == rightX) return glyphPosition
		return when {
			position.x <= leftX -> ops.glyphPositionAtCoordinate(leftX + 1f, position.y)
			position.x >= rightX -> ops.glyphPositionAtCoordinate(rightX - 1f, position.y)
			else -> glyphPosition
		}
	}

	override fun getRangeForRect(rect: Rect, granularity: TextGranularity, inclusionStrategy: TextInclusionStrategy): TextRange =
		TextRange.Zero

	override fun getBoundingBox(offset: Int): Rect {
		val box = getBoxForwardByOffset(offset) ?: getBoxBackwardByOffset(offset, text.length)
		return box?.let { Rect(it.left, it.top, it.right, it.bottom) } ?: Rect(0f, 0f, 0f, 0f)
	}

	override fun fillBoundingBoxes(range: TextRange, array: FloatArray, arrayStart: Int) {
		// TODO(CMP-720): not supported by skiko paragraph yet.
	}

	override fun getWordBoundary(offset: Int): TextRange {
		if (offset < text.length && text[offset].isWhitespace() || offset == text.length) {
			return if (offset > 0 && !text[offset - 1].isWhitespace()) {
				val b = ops.wordBoundary(offset - 1); TextRange(b[0], b[1])
			} else TextRange(offset, offset)
		}
		val b = ops.wordBoundary(offset)
		return TextRange(b[0], b[1])
	}

	override fun paint(canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?) {
		ops.rebuildAndPaint(canvas, color, shadow ?: style.shadow, textDecoration ?: style.textDecoration)
	}

	override fun paint(
		canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?,
		drawStyle: DrawStyle?, blendMode: BlendMode,
	) {
		ops.rebuildAndPaint(canvas, color, shadow ?: style.shadow, textDecoration ?: style.textDecoration)
	}

	override fun paint(
		canvas: Canvas, brush: Brush, alpha: Float, shadow: Shadow?, textDecoration: TextDecoration?,
		drawStyle: DrawStyle?, blendMode: BlendMode,
	) {
		val color = (brush as? SolidColor)?.value?.let {
			if (alpha.isFinite()) it.copy(alpha = it.alpha * alpha) else it
		} ?: style.color
		ops.rebuildAndPaint(canvas, color, shadow ?: style.shadow, textDecoration ?: style.textDecoration)
	}

	// ============
	//  Line-metric helpers

	private val lineMetrics: List<LineMetricData> by lazy { ops.lineMetrics() }

	private fun lineMetricsForOffset(offset: Int): LineMetricData? =
		if (offset in 0..text.length) lineMetrics.binarySearchFirstMatchingOrLast { offset < it.endIncludingNewline } else null

	private fun lineMetricsForVerticalPosition(vertical: Float): LineMetricData? =
		lineMetrics.binarySearchFirstMatchingOrLast { vertical < it.baseline + it.descent }

	private fun getBoxForwardByOffset(offset: Int): TextBoxData? {
		if (offset !in 0..text.length) return null
		var to = offset + 1
		while (to <= text.length) {
			ops.getRectsForRange(offset, to, useMaxHeight = false).firstOrNull()?.let { return it }
			to += 1
		}
		return null
	}

	private fun getBoxBackwardByOffset(offset: Int, end: Int = offset): TextBoxData? {
		if (offset !in 0..text.length) return null
		var from = offset - 1
		while (from >= 0) {
			val box = ops.getRectsForRange(from, end, useMaxHeight = false).firstOrNull()
			when {
				box == null -> from -= 1
				text[from] == '\n' -> {
					val bottom = box.bottom + box.bottom - box.top
					return TextBoxData(0f, box.bottom, 0f, bottom, box.isRtl)
				}
				else -> return box
			}
		}
		return null
	}
}

private fun TextBoxData.cursorHorizontalPosition(opposite: Boolean = false): Float =
	if (isRtl) (if (opposite) right else left) else (if (opposite) left else right)

private inline fun <T> List<T>.binarySearchFirstMatchingOrLast(crossinline predicate: (T) -> Boolean): T? {
	if (isEmpty()) return null
	val index = binarySearch { if (predicate(it)) 1 else -1 }
	return this[(-index - 1).coerceAtMost(lastIndex)]
}
