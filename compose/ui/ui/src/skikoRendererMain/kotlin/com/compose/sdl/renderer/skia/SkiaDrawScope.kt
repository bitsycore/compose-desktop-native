package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.Path as ComposePath
import com.compose.sdl.graphics.PathCommand
import com.compose.sdl.graphics.gradientCenter
import com.compose.sdl.graphics.gradientColors
import com.compose.sdl.graphics.gradientEnd
import com.compose.sdl.graphics.gradientRadius
import com.compose.sdl.graphics.gradientStart
import com.compose.sdl.graphics.gradientStops
import com.compose.sdl.graphics.gradientTileMode
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.TileMode as ComposeTileMode
import com.compose.sdl.graphics.a8
import com.compose.sdl.graphics.b8
import com.compose.sdl.graphics.g8
import com.compose.sdl.graphics.r8
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap as ComposeStrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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

	// ============
	//  DrawScope contract (upstream shape). Shapes draw via the private *Core
	//  helpers (Skia Canvas); the color overloads wrap the colour in SolidColor.
	//  colorFilter / blendMode / pathEffect accept-and-ignore. drawImage /
	//  drawPoints are no-ops (images paint through the renderer's painter leaf).
	//  drawContext.canvas stays EmptyCanvas — primitives paint direct to fCanvas.

	override val density: Float get() = 1f
	override val fontScale: Float get() = 1f
	override val layoutDirection: LayoutDirection get() = LayoutDirection.Ltr

	override val drawContext: DrawContext = object : DrawContext {
		override var size: Size = this@SkiaDrawScope.size
		override val transform: DrawTransform = object : DrawTransform {
			override val size: Size get() = this@SkiaDrawScope.size
			override val center: Offset get() = Offset(size.width / 2f, size.height / 2f)
			override fun inset(left: Float, top: Float, right: Float, bottom: Float) {}
			override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {}
			override fun clipPath(path: ComposePath, clipOp: ClipOp) {}
			override fun translate(left: Float, top: Float) {}
			override fun rotate(degrees: Float, pivot: Offset) {}
			override fun scale(scaleX: Float, scaleY: Float, pivot: Offset) {}
			override fun transform(matrix: Matrix) {}
		}
	}

	override fun drawRect(brush: Brush, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		rectCore(brush, topLeft, size, alpha, style)
	override fun drawRect(color: ComposeColor, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		rectCore(SolidColor(color), topLeft, size, alpha, style)

	override fun drawCircle(brush: Brush, radius: Float, center: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		circleCore(brush, radius, center, alpha, style)
	override fun drawCircle(color: ComposeColor, radius: Float, center: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		circleCore(SolidColor(color), radius, center, alpha, style)

	override fun drawArc(brush: Brush, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		arcCore(brush, startAngle, sweepAngle, useCenter, topLeft, size, alpha, style)
	override fun drawArc(color: ComposeColor, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		arcCore(SolidColor(color), startAngle, sweepAngle, useCenter, topLeft, size, alpha, style)

	override fun drawOval(brush: Brush, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		ovalCore(brush, topLeft, size, alpha, style)
	override fun drawOval(color: ComposeColor, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		ovalCore(SolidColor(color), topLeft, size, alpha, style)

	override fun drawRoundRect(brush: Brush, topLeft: Offset, size: Size, cornerRadius: CornerRadius, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		roundRectCore(brush, topLeft, size, cornerRadius.x, alpha, style)
	override fun drawRoundRect(color: ComposeColor, topLeft: Offset, size: Size, cornerRadius: CornerRadius, style: DrawStyle, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) =
		roundRectCore(SolidColor(color), topLeft, size, cornerRadius.x, alpha, style)

	override fun drawPath(path: ComposePath, brush: Brush, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		pathCore(path, brush, alpha, style)
	override fun drawPath(path: ComposePath, color: ComposeColor, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		pathCore(path, SolidColor(color), alpha, style)

	override fun drawLine(brush: Brush, start: Offset, end: Offset, strokeWidth: Float, cap: ComposeStrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) =
		lineCore(brush, start, end, strokeWidth, cap, alpha)
	override fun drawLine(color: ComposeColor, start: Offset, end: Offset, strokeWidth: Float, cap: ComposeStrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) =
		lineCore(SolidColor(color), start, end, strokeWidth, cap, alpha)

	override fun drawImage(image: ImageBitmap, topLeft: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {}
	@Deprecated("Use the overload that takes a FilterQuality", level = DeprecationLevel.HIDDEN)
	override fun drawImage(image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset, dstSize: IntSize, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {}
	override fun drawImage(image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset, dstSize: IntSize, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode, filterQuality: FilterQuality) {}

	override fun drawPoints(points: List<Offset>, pointMode: PointMode, color: ComposeColor, strokeWidth: Float, cap: ComposeStrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {}
	override fun drawPoints(points: List<Offset>, pointMode: PointMode, brush: Brush, strokeWidth: Float, cap: ComposeStrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {}

	private fun rectCore(
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

	private fun circleCore(
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

	private fun arcCore(
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

	private fun lineCore(
		brush: Brush,
		start: Offset,
		end: Offset,
		strokeWidth: Float,
		cap: ComposeStrokeCap,
		alpha: Float,
	) {
		val vPaint = paintFor(brush, alpha, Stroke(strokeWidth, cap = cap), Size(0f, 0f))
		fCanvas.drawLine(
			fOriginX + start.x, fOriginY + start.y,
			fOriginX + end.x, fOriginY + end.y,
			vPaint,
		)
		vPaint.close()
	}

	private fun pathCore(
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

	private fun ovalCore(
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

	private fun roundRectCore(
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
		// Path is now an interface (vendored); our concrete impl is ProjectPath
		// in commonMain. Cast to read the PathCommand list directly — falls
		// back to an empty path if a foreign Path implementation slips in.
		val vCommands = (inPath as? com.compose.sdl.graphics.ProjectPath)?.commands ?: emptyList()
		for (vCmd in vCommands) when (vCmd) {
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
					else -> PaintStrokeCap.BUTT
				}
			}
		}
		when (inBrush) {
			is SolidColor -> vPaint.color = inBrush.value.withAlphaScaled(inAlpha).toSkiaColor()
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
			is androidx.compose.ui.graphics.ShaderBrush -> {
				// Generic ShaderBrush — vendored upstream Brush adds this as the
				// extension point for custom shaders. The renderer doesn't have
				// a Skia-Shader bridge for arbitrary `ShaderBrush` subclasses
				// yet (those wrap a `Shader` we don't construct here), so we
				// fall back to a neutral white-alpha fill. The four concrete
				// gradient classes above bypass this branch already.
				vPaint.color = Color.makeARGB(((inAlpha) * 255).toInt().coerceIn(0, 255), 255, 255, 255)
			}
		}
		return vPaint
	}

	// ============
	//  Gradient → Skia Shader. Gradient.Colors wraps the Array<Color4f> +
	//  positions + tile mode + colour space. Float.POSITIVE_INFINITY in
	//  the caller's anchor coordinates means "extend to the shape's bounds".

	private fun makeLinearShader(inB: LinearGradient, inSize: Size): Shader {
		val vStartX = fOriginX + resolveX(inB.gradientStart.x, inSize.width)
		val vStartY = fOriginY + resolveY(inB.gradientStart.y, inSize.height)
		val vEndX = fOriginX + resolveX(inB.gradientEnd.x, inSize.width)
		val vEndY = fOriginY + resolveY(inB.gradientEnd.y, inSize.height)
		return Shader.makeLinearGradient(
			p0 = Point(vStartX, vStartY),
			p1 = Point(vEndX, vEndY),
			gradient = Gradient(skiaColorsFor(inB.gradientColors, inB.gradientStops, inB.gradientTileMode.toSkia())),
		)
	}

	private fun makeRadialShader(inB: RadialGradient, inSize: Size): Shader {
		val vCx = fOriginX + resolveX(inB.gradientCenter.x, inSize.width)
		val vCy = fOriginY + resolveY(inB.gradientCenter.y, inSize.height)
		val vR = if (inB.gradientRadius.isFinite()) inB.gradientRadius else (inSize.minDimension / 2f)
		return Shader.makeRadialGradient(
			center = Point(vCx, vCy),
			radius = vR,
			gradient = Gradient(skiaColorsFor(inB.gradientColors, inB.gradientStops, inB.gradientTileMode.toSkia())),
		)
	}

	private fun makeSweepShader(inB: SweepGradient, inSize: Size): Shader {
		val vCx = fOriginX + resolveX(inB.gradientCenter.x, inSize.width)
		val vCy = fOriginY + resolveY(inB.gradientCenter.y, inSize.height)
		return Shader.makeSweepGradient(
			center = Point(vCx, vCy),
			gradient = Gradient(skiaColorsFor(inB.gradientColors, inB.gradientStops, FilterTileMode.CLAMP)),
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
	else                     -> FilterTileMode.CLAMP
}
