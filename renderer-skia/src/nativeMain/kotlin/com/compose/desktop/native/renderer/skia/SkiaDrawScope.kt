package com.compose.desktop.native.renderer.skia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.PathCommand
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.TileMode as ComposeTileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.StrokeCap as ComposeStrokeCap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Path as SkiaPath
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Point
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader

// ==================
// MARK: SkiaDrawScope
// ==================

/* DrawScope implementation that translates the common-side primitives into
   native Skia Canvas calls. Each method allocates a Paint, configures
   fill / stroke style + shader, draws, then closes the Paint — Skia's
   ref-counting frees the underlying SkPaint promptly. */
internal class SkiaDrawScope(
	private val fCanvas: Canvas,
	private val fOriginX: Float,
	private val fOriginY: Float,
	override val size: Size,
) : DrawScope {

	override fun drawRect(
		brush: Brush,
		topLeft: Offset,
		size: Size,
		alpha: Float,
		style: DrawStyle,
	) {
		val vPaint = paintFor(brush, alpha, style, size)
		val vRect = Rect.makeXYWH(
			fOriginX + topLeft.x,
			fOriginY + topLeft.y,
			size.width,
			size.height,
		)
		fCanvas.drawRect(vRect, vPaint)
		vPaint.close()
	}

	override fun drawCircle(
		brush: Brush,
		radius: Float,
		center: Offset,
		alpha: Float,
		style: DrawStyle,
	) {
		val vPaint = paintFor(brush, alpha, style, Size(radius * 2f, radius * 2f))
		fCanvas.drawCircle(
			fOriginX + center.x,
			fOriginY + center.y,
			radius,
			vPaint,
		)
		vPaint.close()
	}

	override fun drawArc(
		brush: Brush,
		startAngle: Float,
		sweepAngle: Float,
		useCenter: Boolean,
		topLeft: Offset,
		size: Size,
		alpha: Float,
		style: DrawStyle,
	) {
		val vPaint = paintFor(brush, alpha, style, size)
		val vRect = Rect.makeXYWH(
			fOriginX + topLeft.x,
			fOriginY + topLeft.y,
			size.width,
			size.height,
		)
		fCanvas.drawArc(
			vRect.left, vRect.top, vRect.right, vRect.bottom,
			startAngle, sweepAngle, useCenter, vPaint,
		)
		vPaint.close()
	}

	override fun drawLine(
		brush: Brush,
		start: Offset,
		end: Offset,
		strokeWidth: Float,
		cap: ComposeStrokeCap,
		alpha: Float,
	) {
		val vPaint = paintFor(brush, alpha, Stroke(strokeWidth, cap), Size(0f, 0f))
		fCanvas.drawLine(
			fOriginX + start.x, fOriginY + start.y,
			fOriginX + end.x, fOriginY + end.y,
			vPaint,
		)
		vPaint.close()
	}

	override fun drawPath(
		path: ComposePath,
		brush: Brush,
		alpha: Float,
		style: DrawStyle,
	) {
		val vPaint = paintFor(brush, alpha, style, size)
		val vSk = toSkiaPath(path)
		fCanvas.drawPath(vSk, vPaint)
		vSk.close()
		vPaint.close()
	}

	override fun drawOval(
		brush: Brush,
		topLeft: Offset,
		size: Size,
		alpha: Float,
		style: DrawStyle,
	) {
		val vPaint = paintFor(brush, alpha, style, size)
		val vRect = Rect.makeXYWH(
			fOriginX + topLeft.x, fOriginY + topLeft.y,
			size.width, size.height,
		)
		fCanvas.drawOval(vRect, vPaint)
		vPaint.close()
	}

	override fun drawRoundRect(
		brush: Brush,
		topLeft: Offset,
		size: Size,
		cornerRadius: Float,
		alpha: Float,
		style: DrawStyle,
	) {
		val vPaint = paintFor(brush, alpha, style, size)
		val vRR = RRect.makeXYWH(
			fOriginX + topLeft.x, fOriginY + topLeft.y,
			size.width, size.height, cornerRadius,
		)
		fCanvas.drawRRect(vRR, vPaint)
		vPaint.close()
	}

	private fun toSkiaPath(inPath: ComposePath): SkiaPath {
		val vB = PathBuilder()
		for (vCmd in inPath.commands) when (vCmd) {
			is PathCommand.MoveTo  -> vB.moveTo(fOriginX + vCmd.x, fOriginY + vCmd.y)
			is PathCommand.LineTo  -> vB.lineTo(fOriginX + vCmd.x, fOriginY + vCmd.y)
			is PathCommand.QuadTo  -> vB.quadTo(
				fOriginX + vCmd.cx, fOriginY + vCmd.cy,
				fOriginX + vCmd.x,  fOriginY + vCmd.y,
			)
			is PathCommand.CubicTo -> vB.cubicTo(
				fOriginX + vCmd.c1x, fOriginY + vCmd.c1y,
				fOriginX + vCmd.c2x, fOriginY + vCmd.c2y,
				fOriginX + vCmd.x,   fOriginY + vCmd.y,
			)
			PathCommand.Close      -> vB.closePath()
		}
		return vB.snapshot()
	}

	// ============
	//  Paint factory — wires colour / brush / style / cap onto a fresh
	//  Skia Paint. inShapeSize is used to resolve gradient anchor points
	//  that the caller left at Float.POSITIVE_INFINITY ("full bounds").

	private fun paintFor(
		inBrush: Brush,
		inAlpha: Float,
		inStyle: DrawStyle,
		inShapeSize: Size,
	): Paint {
		val vPaint = Paint().apply { isAntiAlias = true }
		when (inStyle) {
			Fill -> vPaint.mode = PaintMode.FILL
			is Stroke -> {
				vPaint.mode = PaintMode.STROKE
				vPaint.strokeWidth = inStyle.width
				vPaint.strokeCap = when (inStyle.cap) {
					ComposeStrokeCap.Butt -> PaintStrokeCap.BUTT
					ComposeStrokeCap.Round -> PaintStrokeCap.ROUND
					ComposeStrokeCap.Square -> PaintStrokeCap.SQUARE
				}
			}
		}
		when (inBrush) {
			is SolidColor -> vPaint.color = inBrush.color.withAlphaScaled(inAlpha).toSkiaColor()
			is LinearGradient -> {
				vPaint.color = Color.makeARGB(((inAlpha) * 255).toInt().coerceIn(0, 255), 255, 255, 255)
				vPaint.shader = makeLinearShader(inBrush, inShapeSize)
			}
			is RadialGradient -> {
				vPaint.color = Color.makeARGB(((inAlpha) * 255).toInt().coerceIn(0, 255), 255, 255, 255)
				vPaint.shader = makeRadialShader(inBrush, inShapeSize)
			}
			is SweepGradient -> {
				vPaint.color = Color.makeARGB(((inAlpha) * 255).toInt().coerceIn(0, 255), 255, 255, 255)
				vPaint.shader = makeSweepShader(inBrush, inShapeSize)
			}
		}
		return vPaint
	}

	// ============
	//  Gradient → Skia Shader. Gradient.Colors wraps the Array<Color4f> +
	//  positions + tile mode + colour space. Float.POSITIVE_INFINITY in
	//  the caller's anchor coordinates means "extend to the shape's bounds".

	private fun makeLinearShader(inB: LinearGradient, inSize: Size): Shader {
		val vStartX = fOriginX + resolveX(inB.start.x, inSize.width)
		val vStartY = fOriginY + resolveY(inB.start.y, inSize.height)
		val vEndX = fOriginX + resolveX(inB.end.x, inSize.width)
		val vEndY = fOriginY + resolveY(inB.end.y, inSize.height)
		return Shader.makeLinearGradient(
			p0 = Point(vStartX, vStartY),
			p1 = Point(vEndX, vEndY),
			gradient = Gradient(skiaColorsFor(inB.colors, inB.stops, inB.tileMode.toSkia())),
		)
	}

	private fun makeRadialShader(inB: RadialGradient, inSize: Size): Shader {
		val vCx = fOriginX + resolveX(inB.center.x, inSize.width)
		val vCy = fOriginY + resolveY(inB.center.y, inSize.height)
		val vR = if (inB.radius.isFinite()) inB.radius else (inSize.minDimension / 2f)
		return Shader.makeRadialGradient(
			center = Point(vCx, vCy),
			radius = vR,
			gradient = Gradient(skiaColorsFor(inB.colors, inB.stops, inB.tileMode.toSkia())),
		)
	}

	private fun makeSweepShader(inB: SweepGradient, inSize: Size): Shader {
		val vCx = fOriginX + resolveX(inB.center.x, inSize.width)
		val vCy = fOriginY + resolveY(inB.center.y, inSize.height)
		return Shader.makeSweepGradient(
			center = Point(vCx, vCy),
			gradient = Gradient(skiaColorsFor(inB.colors, inB.stops, FilterTileMode.CLAMP)),
		)
	}

	private fun skiaColorsFor(
		inColors: List<ComposeColor>,
		inStops: List<Float>?,
		inTile: FilterTileMode,
	): Gradient.Colors = Gradient.Colors(
		colors = Array(inColors.size) { inColors[it].toColor4f() },
		positions = inStops?.toFloatArray(),
		tileMode = inTile,
	)

	private fun resolveX(inV: Float, inW: Float): Float = if (inV.isFinite()) inV else inW
	private fun resolveY(inV: Float, inH: Float): Float = if (inV.isFinite()) inV else inH
}

// ==================
// MARK: Helpers
// ==================

private fun ComposeColor.toSkiaColor(): Int =
	Color.makeARGB(a8, r8, g8, b8)

private fun ComposeColor.toColor4f(): Color4f =
	Color4f(r = r8 / 255f, g = g8 / 255f, b = b8 / 255f, a = a8 / 255f)

private fun ComposeColor.withAlphaScaled(inAlpha: Float): ComposeColor =
	if (inAlpha >= 1f) this else copy(alpha = alpha * inAlpha)

private fun ComposeTileMode.toSkia(): FilterTileMode = when (this) {
	ComposeTileMode.Clamp    -> FilterTileMode.CLAMP
	ComposeTileMode.Repeated -> FilterTileMode.REPEAT
	ComposeTileMode.Mirror   -> FilterTileMode.MIRROR
	ComposeTileMode.Decal    -> FilterTileMode.DECAL
}
