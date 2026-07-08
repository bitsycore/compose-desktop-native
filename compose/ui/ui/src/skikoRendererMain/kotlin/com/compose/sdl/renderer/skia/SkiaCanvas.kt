package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.SkiaBackedPath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.TileMode as ComposeTileMode
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap as ComposeStrokeCap
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.toSkia
import com.compose.sdl.graphics.PathCommand
import com.compose.sdl.graphics.ProjectPath
import com.compose.sdl.graphics.a8
import com.compose.sdl.graphics.b8
import com.compose.sdl.graphics.g8
import com.compose.sdl.graphics.gradientCenter
import com.compose.sdl.graphics.gradientColors
import com.compose.sdl.graphics.gradientEnd
import com.compose.sdl.graphics.gradientRadius
import com.compose.sdl.graphics.gradientStart
import com.compose.sdl.graphics.gradientStops
import com.compose.sdl.graphics.gradientTileMode
import com.compose.sdl.graphics.r8
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Color as SkColor
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Point
import org.jetbrains.skia.Shader as SkShader
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect as SkRect

// ==================
// MARK: SkiaCanvas
// ==================

/*
 Phase 9 — Skia implementation of `androidx.compose.ui.graphics.Canvas`, mirror of
 `Sdl3Canvas`. Wraps a live `org.jetbrains.skia.Canvas` (from the active
 SkiaBridge — Metal / OpenGL / CPU raster) and translates every upstream draw
 call into a Skia primitive.

 Text + painter bridges delegate to SkiaTextRenderer / SkiaImageCache exactly
 like Sdl3Canvas. Gradient brushes are currently solid-color fallback (Skia's
 Shader API takes a Gradient descriptor that we don't build yet).
*/
internal class SkiaCanvas(
	private val fCanvas: SkCanvas,
	@Suppress("UNUSED_PARAMETER") inSize: Size,
	private val fTextRenderer: SkiaTextRenderer? = null,
	private val fImageCache: SkiaImageCache? = null,
) : Canvas,
	com.compose.sdl.text.NativeTextCanvas,
	com.compose.sdl.graphics.NativePainterCanvas,
	com.compose.sdl.graphics.NativeShadowCanvas {

	fun finish() { /* Skia save/restore is balanced per call */ }

	// ============
	//  Drop shadow (NativeShadowCanvas) — a real Gaussian blur MaskFilter on
	//  the layer's outline, offset downward for the spot-light look. Called by
	//  ProjectOwnedLayer in layer-local coords (canvas already translated).

	override fun drawDropShadow(
		inOutline: androidx.compose.ui.graphics.Outline,
		inElevationPx: Float,
		inAmbientColor: androidx.compose.ui.graphics.Color,
		inSpotColor: androidx.compose.ui.graphics.Color,
	) {
		if (inElevationPx <= 0f) return
		val vOffsetY = inElevationPx * 0.4f
		val vPaint = SkPaint().apply {
			color = toSkiaColor(inSpotColor.copy(alpha = 0.28f * inSpotColor.alpha))
			maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
				org.jetbrains.skia.FilterBlurMode.NORMAL,
				sigma = inElevationPx * 0.5f + 0.5f,
			)
		}
		when (inOutline) {
			is androidx.compose.ui.graphics.Outline.Rectangle -> {
				val vR = inOutline.rect
				fCanvas.drawRRect(
					org.jetbrains.skia.RRect.makeLTRB(vR.left, vR.top + vOffsetY, vR.right, vR.bottom + vOffsetY, 0f),
					vPaint,
				)
			}
			is androidx.compose.ui.graphics.Outline.Rounded -> {
				val vRr = inOutline.roundRect
				fCanvas.drawRRect(
					org.jetbrains.skia.RRect.makeLTRB(
						vRr.left, vRr.top + vOffsetY, vRr.right, vRr.bottom + vOffsetY,
						vRr.topLeftCornerRadius.x,
					),
					vPaint,
				)
			}
			is androidx.compose.ui.graphics.Outline.Generic -> {
				// Blur the ACTUAL path — CutCornerShape / GenericShape shadows
				// follow the real outline instead of the bounding rect.
				fCanvas.save()
				fCanvas.translate(0f, vOffsetY)
				fCanvas.drawPath(toSkiaPath(inOutline.path), vPaint)
				fCanvas.restore()
			}
		}
		vPaint.close()
	}

	// ============
	//  Transform / clip state — delegated to Skia's own stack.

	override fun save() { fCanvas.save() }
	override fun restore() { fCanvas.restore() }
	override fun saveLayer(bounds: Rect, paint: Paint) {
		// Ignore caller-computed bounds and pass a huge rect. ProjectOwnedLayer.drawLayer
		// computes bounds from THIS layer's own fPosition + fTranslation + fSize —
		// it can't see inner-layer translations that would paint outside those bounds.
		// Concrete failure: dragging a tab with `graphicsLayer(alpha=0.65f, translationX=X)`
		// — the layer's saveLayer bounds don't include any subsequent inner-layer
		// translation, so on Skia GPU backends the offscreen buffer is allocated too
		// small and the ghost gets clipped as it slides. Even with the single-layer app
		// fix, Skia's saveLayer(bounds, alphaPaint) allocates an offscreen SIZED to
		// bounds — passing tight (fPos + fTrans + size) bounds is exactly the layer's
		// own painted rect, so anything drawn OUTSIDE that rect via child modifiers
		// (padding overshoot, border stroke overshoot, drop shadow) is trimmed.
		// A large explicit rect avoids overload resolution ambiguity that `null as
		// SkRect?` had (didn't seem to hit the null branch consistently). Alpha compositing
		// still works — Skia does the alpha modulation via layerPaint on restore().
		// SaveLayer paint carries alpha (for compositing) — no shape geometry, so
		// pass zero size / origin; the gradient/shader branches don't fire for a
		// layer paint.
		val vLayerPaint = toSkiaPaint(paint, Size(0f, 0f), Offset.Zero)
		fCanvas.saveLayer(-100_000f, -100_000f, 100_000f, 100_000f, vLayerPaint)
		vLayerPaint.close()
	}
	override fun translate(dx: Float, dy: Float) { fCanvas.translate(dx, dy) }
	override fun scale(sx: Float, sy: Float) { fCanvas.scale(sx, sy) }
	override fun rotate(degrees: Float) { fCanvas.rotate(degrees) }
	override fun skew(sx: Float, sy: Float) { fCanvas.skew(sx, sy) }
	override fun concat(matrix: Matrix) {
		val vM = org.jetbrains.skia.Matrix33(
			matrix.values[0], matrix.values[4], matrix.values[12],
			matrix.values[1], matrix.values[5], matrix.values[13],
			matrix.values[3], matrix.values[7], matrix.values[15],
		)
		fCanvas.concat(vM)
	}

	override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
		fCanvas.clipRect(
			SkRect.makeLTRB(left, top, right, bottom),
			if (clipOp == ClipOp.Difference) ClipMode.DIFFERENCE else ClipMode.INTERSECT,
			true,
		)
	}

	override fun clipPath(path: ComposePath, clipOp: ClipOp) {
		fCanvas.clipPath(
			toSkiaPath(path),
			if (clipOp == ClipOp.Difference) ClipMode.DIFFERENCE else ClipMode.INTERSECT,
			true,
		)
	}

	// ============
	//  Draw primitives.

	override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		val vSize = Size(right - left, bottom - top)
		val vSk = toSkiaPaint(paint, vSize, Offset(left, top))
		fCanvas.drawRect(SkRect.makeLTRB(left, top, right, bottom), vSk)
		vSk.close()
	}

	override fun drawRoundRect(
		left: Float, top: Float, right: Float, bottom: Float,
		radiusX: Float, radiusY: Float, paint: Paint,
	) {
		val vSize = Size(right - left, bottom - top)
		val vSk = toSkiaPaint(paint, vSize, Offset(left, top))
		fCanvas.drawRRect(
			RRect.makeLTRB(left, top, right, bottom, radiusX, radiusY),
			vSk,
		)
		vSk.close()
	}

	override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		val vSize = Size(right - left, bottom - top)
		val vSk = toSkiaPaint(paint, vSize, Offset(left, top))
		fCanvas.drawOval(SkRect.makeLTRB(left, top, right, bottom), vSk)
		vSk.close()
	}

	override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
		val vSize = Size(radius * 2f, radius * 2f)
		val vSk = toSkiaPaint(paint, vSize, Offset(center.x - radius, center.y - radius))
		fCanvas.drawCircle(center.x, center.y, radius, vSk)
		vSk.close()
	}

	override fun drawArc(
		left: Float, top: Float, right: Float, bottom: Float,
		startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint,
	) {
		val vSize = Size(right - left, bottom - top)
		val vSk = toSkiaPaint(paint, vSize, Offset(left, top))
		fCanvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, vSk)
		vSk.close()
	}

	override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
		val vSk = toSkiaPaint(paint, Size(0f, 0f), Offset.Zero)
		fCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, vSk)
		vSk.close()
	}

	override fun drawPath(path: ComposePath, paint: Paint) {
		val vSk = toSkiaPaint(paint, Size(0f, 0f), Offset.Zero)
		fCanvas.drawPath(toSkiaPath(path), vSk)
		vSk.close()
	}
	// ============
	//  Native bridges.

	override fun drawNativeText(
		inText: String,
		inSpans: List<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>>?,
		inX: Float,
		inY: Float,
		inBoxWidth: Float,
		inBoxHeight: Float,
		inColor: ComposeColor,
		inFontSizePx: Int,
		inTextAlign: androidx.compose.ui.text.style.TextAlign,
		inSoftWrap: Boolean,
		inFontFamily: String?,
		inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>?,
		inBaseItalic: Boolean,
		inTextDecoration: androidx.compose.ui.text.style.TextDecoration?,
	) {
		val vUnderline = inTextDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.Underline) == true
		val vLineThrough = inTextDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) == true
		fTextRenderer?.drawText(
			inCanvas = fCanvas,
			inText = inText,
			inX = inX,
			inY = inY,
			inBoxWidth = inBoxWidth.toInt(),
			inBoxHeight = inBoxHeight.toInt(),
			inColor = inColor,
			inFontSize = inFontSizePx,
			inAlign = inTextAlign,
			inSoftWrap = inSoftWrap,
			inFontFamily = inFontFamily,
			inFontVariations = inFontVariations,
			inSpans = inSpans,
			inBaseItalic = inBaseItalic,
			inBaseUnderline = vUnderline,
			inBaseLineThrough = vLineThrough,
		)
	}

	override fun drawNativePainter(
		inResourcePath: String,
		inKind: com.compose.sdl.res.ResourceKind,
		inX: Float,
		inY: Float,
		inWidth: Float,
		inHeight: Float,
		inContentScale: androidx.compose.ui.layout.ContentScale,
		inAlpha: Float,
	) {
		fImageCache?.draw(
			inCanvas = fCanvas,
			inPath = inResourcePath,
			inKind = inKind,
			inX = inX,
			inY = inY,
			inW = inWidth,
			inH = inHeight,
			inScale = inContentScale,
			inAlpha = inAlpha,
		)
	}

	// ============
	//  Native image (ImageBitmap-backed vector — see SkiaOffscreen). The offscreen
	//  registers itself lazily, so anything not backed by a SkiaImageBitmap surface
	//  (a hypothetical stub) is silently skipped.

	override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) {
		val vBmp = image as? SkiaImageBitmap ?: return
		val vImg = vBmp.snapshot()
		val vSkPaint = imageBlitPaint(paint)
		fCanvas.drawImage(vImg, topLeftOffset.x, topLeftOffset.y, vSkPaint)
		vSkPaint.close()
	}

	override fun drawImageRect(
		image: ImageBitmap,
		srcOffset: androidx.compose.ui.unit.IntOffset,
		srcSize: androidx.compose.ui.unit.IntSize,
		dstOffset: androidx.compose.ui.unit.IntOffset,
		dstSize: androidx.compose.ui.unit.IntSize,
		paint: Paint,
	) {
		val vBmp = image as? SkiaImageBitmap ?: return
		val vImg = vBmp.snapshot()
		val vSrc = SkRect.makeXYWH(
			srcOffset.x.toFloat(), srcOffset.y.toFloat(),
			srcSize.width.toFloat(), srcSize.height.toFloat(),
		)
		val vDst = SkRect.makeXYWH(
			dstOffset.x.toFloat(), dstOffset.y.toFloat(),
			dstSize.width.toFloat(), dstSize.height.toFloat(),
		)
		val vSkPaint = imageBlitPaint(paint)
		fCanvas.drawImageRect(vImg, vSrc, vDst, vSkPaint, true)
		vSkPaint.close()
	}

	// Blit paint: alpha modulated as the paint colour's alpha; ColorFilter (Icon
	// tint arrives here) forwarded to Skia so the vendored VectorPainter's
	// ColorFilter.tint recolours the icon at composite time.
	private fun imageBlitPaint(inPaint: Paint): SkPaint {
		val vP = SkPaint()
		vP.color = SkColor.makeARGB(
			(inPaint.alpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255,
		)
		vP.isAntiAlias = true
		inPaint.colorFilter?.let {
			vP.colorFilter = it.asSkiaColorFilter()
		}
		return vP
	}

	override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) {}
	override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) {}
	override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) {}
	override fun enableZ() {}
	override fun disableZ() {}

	// ============
	//  Paint translation. Recovers a gradient Brush stashed on paint.shader (see
	//  CanvasPaintActuals.native.kt — ShaderBrush.applyTo drops everything but
	//  color=Black otherwise) and builds the matching Skia Shader, using the
	//  shape's [inShapeSize] to resolve infinite-anchor gradients. Forwards
	//  paint.colorFilter and paint.blendMode (BlendMode.Clear zeroes the region,
	//  which DrawCache relies on to clear the offscreen before rasterising a
	//  vector).

	private fun toSkiaPaint(inPaint: Paint, inShapeSize: Size, inShapeOrigin: Offset): SkPaint {
		val vPaint = SkPaint()
		vPaint.isAntiAlias = true
		vPaint.mode = if (inPaint.style == PaintingStyle.Stroke) PaintMode.STROKE else PaintMode.FILL
		vPaint.strokeWidth = inPaint.strokeWidth
		vPaint.strokeCap = when (inPaint.strokeCap) {
			ComposeStrokeCap.Round -> PaintStrokeCap.ROUND
			ComposeStrokeCap.Square -> PaintStrokeCap.SQUARE
			else -> PaintStrokeCap.BUTT
		}
		val vBrush = inPaint.shader?.brush
		val vBaseColor = SkColor.makeARGB(
			(inPaint.color.alpha * inPaint.alpha * 255f).toInt().coerceIn(0, 255),
			(inPaint.color.red * 255f).toInt().coerceIn(0, 255),
			(inPaint.color.green * 255f).toInt().coerceIn(0, 255),
			(inPaint.color.blue * 255f).toInt().coerceIn(0, 255),
		)
		when (vBrush) {
			null, is SolidColor -> {
				vPaint.color = vBaseColor
			}
			is LinearGradient -> {
				vPaint.color = whiteAlpha(inPaint.alpha)
				vPaint.shader = makeLinearShader(vBrush, inShapeSize, inShapeOrigin)
			}
			is RadialGradient -> {
				vPaint.color = whiteAlpha(inPaint.alpha)
				vPaint.shader = makeRadialShader(vBrush, inShapeSize, inShapeOrigin)
			}
			is SweepGradient -> {
				vPaint.color = whiteAlpha(inPaint.alpha)
				vPaint.shader = makeSweepShader(vBrush, inShapeSize, inShapeOrigin)
			}
			else -> {
				vPaint.color = vBaseColor
			}
		}
		inPaint.colorFilter?.let { vPaint.colorFilter = it.asSkiaColorFilter() }
		vPaint.blendMode = inPaint.blendMode.toSkia()
		return vPaint
	}

	private fun whiteAlpha(inAlpha: Float): Int =
		SkColor.makeARGB((inAlpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255)

	private fun makeLinearShader(inB: LinearGradient, inSize: Size, inOrigin: Offset): SkShader {
		val vSx = inOrigin.x + resolveX(inB.gradientStart.x, inSize.width)
		val vSy = inOrigin.y + resolveY(inB.gradientStart.y, inSize.height)
		val vEx = inOrigin.x + resolveX(inB.gradientEnd.x, inSize.width)
		val vEy = inOrigin.y + resolveY(inB.gradientEnd.y, inSize.height)
		return SkShader.makeLinearGradient(
			p0 = Point(vSx, vSy),
			p1 = Point(vEx, vEy),
			gradient = Gradient(skiaColorsFor(inB.gradientColors, inB.gradientStops, inB.gradientTileMode.toSkia())),
		)
	}

	private fun makeRadialShader(inB: RadialGradient, inSize: Size, inOrigin: Offset): SkShader {
		val vCx = inOrigin.x + resolveX(inB.gradientCenter.x, inSize.width)
		val vCy = inOrigin.y + resolveY(inB.gradientCenter.y, inSize.height)
		val vR = if (inB.gradientRadius.isFinite()) inB.gradientRadius else (inSize.minDimension / 2f)
		return SkShader.makeRadialGradient(
			center = Point(vCx, vCy),
			radius = vR,
			gradient = Gradient(skiaColorsFor(inB.gradientColors, inB.gradientStops, inB.gradientTileMode.toSkia())),
		)
	}

	private fun makeSweepShader(inB: SweepGradient, inSize: Size, inOrigin: Offset): SkShader {
		val vCx = inOrigin.x + resolveX(inB.gradientCenter.x, inSize.width)
		val vCy = inOrigin.y + resolveY(inB.gradientCenter.y, inSize.height)
		return SkShader.makeSweepGradient(
			center = Point(vCx, vCy),
			gradient = Gradient(skiaColorsFor(inB.gradientColors, inB.gradientStops, FilterTileMode.CLAMP)),
		)
	}

	private fun skiaColorsFor(
		inColors: List<ComposeColor>,
		inStops: List<Float>?,
		inTile: FilterTileMode,
	): Gradient.Colors = Gradient.Colors(
		colors = Array(inColors.size) { Color4f(r = inColors[it].r8 / 255f, g = inColors[it].g8 / 255f, b = inColors[it].b8 / 255f, a = inColors[it].a8 / 255f) },
		positions = inStops?.toFloatArray(),
		tileMode = inTile,
	)

	private fun resolveX(inV: Float, inW: Float): Float = if (inV.isFinite()) inV else inW
	private fun resolveY(inV: Float, inH: Float): Float = if (inV.isFinite()) inV else inH

	private fun ComposeTileMode.toSkia(): FilterTileMode = when (this) {
		ComposeTileMode.Clamp    -> FilterTileMode.CLAMP
		ComposeTileMode.Repeated -> FilterTileMode.REPEAT
		ComposeTileMode.Mirror   -> FilterTileMode.MIRROR
		ComposeTileMode.Decal    -> FilterTileMode.DECAL
		else                     -> FilterTileMode.CLAMP
	}

	// ============
	//  Path translation. Two possible source types since our skikoRenderer source
	//  set inherits BOTH the commonMain `ProjectPath` and the vendored
	//  `SkiaBackedPath` (`actual fun Path(): Path = SkiaBackedPath()` lives in
	//  vendor/skikoRenderer/…/SkiaBackedPath.skiko.kt). Upstream callers that build
	//  a Path via `Path()` — including `MultiParagraph.getPathForRange`, which
	//  drives text-selection highlight rendering — get a SkiaBackedPath. Our own
	//  `ProjectPath` still surfaces from code paths that build via
	//  `com.compose.sdl.graphics.ProjectPath()` directly. Try
	//  SkiaBackedPath's own Skia handle first (zero-copy, avoids replaying a
	//  command list); otherwise, replay ProjectPath's commands.
	//
	//  Regression that motivated the branch: selection highlight painted an empty
	//  Skia path — the SkiaBackedPath fell through the `as? ProjectPath` check and
	//  returned an empty PathBuilder snapshot.

	private fun toSkiaPath(inPath: ComposePath): SkPath {
		(inPath as? SkiaBackedPath)?.let { return (it as ComposePath).asSkiaPath() }
		val vB = PathBuilder()
		val vCommands = (inPath as? ProjectPath)?.commands ?: return vB.snapshot().also { vB.close() }
		for (vCmd in vCommands) when (vCmd) {
			is PathCommand.MoveTo -> vB.moveTo(vCmd.x, vCmd.y)
			is PathCommand.LineTo -> vB.lineTo(vCmd.x, vCmd.y)
			is PathCommand.QuadTo -> vB.quadTo(vCmd.cx, vCmd.cy, vCmd.x, vCmd.y)
			is PathCommand.CubicTo -> vB.cubicTo(vCmd.c1x, vCmd.c1y, vCmd.c2x, vCmd.c2y, vCmd.x, vCmd.y)
			PathCommand.Close -> vB.closePath()
		}
		return vB.snapshot().also { vB.close() }
	}
}
