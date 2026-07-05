package com.compose.desktop.native.renderer.skia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap as ComposeStrokeCap
import androidx.compose.ui.graphics.Vertices
import com.compose.desktop.native.graphics.PathCommand
import com.compose.desktop.native.graphics.ProjectPath
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Color as SkColor
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.PathBuilder
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
	com.compose.desktop.native.text.NativeTextCanvas,
	com.compose.desktop.native.graphics.NativePainterCanvas {

	fun finish() { /* Skia save/restore is balanced per call */ }

	// ============
	//  Transform / clip state — delegated to Skia's own stack.

	override fun save() { fCanvas.save() }
	override fun restore() { fCanvas.restore() }
	override fun saveLayer(bounds: Rect, paint: Paint) {
		// Pass null bounds instead of the caller-computed rect. Compose's Canvas
		// interface forces a non-null Rect on this API, but `ProjectOwnedLayer.drawLayer`
		// computes bounds from THIS layer's own fPosition + fTranslation + fSize —
		// it can't see inner-layer translations that would paint outside those bounds
		// (e.g. `Modifier.graphicsLayer(translationX=200).clip(...)` inside a
		// `.alpha(0.65f)` wrapping layer: alpha's saveLayer bounds don't know the inner
		// translation of 200px, so the dragged-tab ghost gets clipped by the alpha
		// layer's backbuffer, disappearing as the drag slides it out of the original
		// container's rect). Skia's saveLayer(null, paint) uses the current clip as
		// the layer extent — memory-heavier but correct. Bounds are a "hint" per Skia
		// docs, but GPU backends (Metal/OpenGL) do use them to allocate the offscreen.
		fCanvas.saveLayer(null as SkRect?, toSkiaPaint(paint))
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
		fCanvas.drawRect(SkRect.makeLTRB(left, top, right, bottom), toSkiaPaint(paint))
	}

	override fun drawRoundRect(
		left: Float, top: Float, right: Float, bottom: Float,
		radiusX: Float, radiusY: Float, paint: Paint,
	) {
		fCanvas.drawRRect(
			RRect.makeLTRB(left, top, right, bottom, radiusX, radiusY),
			toSkiaPaint(paint),
		)
	}

	override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		fCanvas.drawOval(SkRect.makeLTRB(left, top, right, bottom), toSkiaPaint(paint))
	}

	override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
		fCanvas.drawCircle(center.x, center.y, radius, toSkiaPaint(paint))
	}

	override fun drawArc(
		left: Float, top: Float, right: Float, bottom: Float,
		startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint,
	) {
		fCanvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, toSkiaPaint(paint))
	}

	override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
		fCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, toSkiaPaint(paint))
	}

	override fun drawPath(path: ComposePath, paint: Paint) {
		fCanvas.drawPath(toSkiaPath(path), toSkiaPaint(paint))
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
	) {
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
		)
	}

	override fun drawNativePainter(
		inResourcePath: String,
		inKind: com.compose.desktop.native.res.ResourceKind,
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
	//  Not-yet-wired ops — accept-and-ignore.

	override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) {}
	override fun drawImageRect(
		image: ImageBitmap,
		srcOffset: androidx.compose.ui.unit.IntOffset,
		srcSize: androidx.compose.ui.unit.IntSize,
		dstOffset: androidx.compose.ui.unit.IntOffset,
		dstSize: androidx.compose.ui.unit.IntSize,
		paint: Paint,
	) {}
	override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) {}
	override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) {}
	override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) {}
	override fun enableZ() {}
	override fun disableZ() {}

	// ============
	//  Paint translation. Solid color today; gradients fall back to Paint.color.

	private fun toSkiaPaint(inPaint: Paint): SkPaint {
		val vPaint = SkPaint()
		val vC = inPaint.color
		vPaint.color = SkColor.makeARGB(
			(vC.alpha * inPaint.alpha * 255f).toInt().coerceIn(0, 255),
			(vC.red * 255f).toInt().coerceIn(0, 255),
			(vC.green * 255f).toInt().coerceIn(0, 255),
			(vC.blue * 255f).toInt().coerceIn(0, 255),
		)
		vPaint.isAntiAlias = true
		vPaint.mode = if (inPaint.style == PaintingStyle.Stroke) PaintMode.STROKE else PaintMode.FILL
		vPaint.strokeWidth = inPaint.strokeWidth
		vPaint.strokeCap = when (inPaint.strokeCap) {
			ComposeStrokeCap.Round -> PaintStrokeCap.ROUND
			ComposeStrokeCap.Square -> PaintStrokeCap.SQUARE
			else -> PaintStrokeCap.BUTT
		}
		return vPaint
	}

	// ============
	//  Path translation from our ProjectPath command list into a Skia Path.

	private fun toSkiaPath(inPath: ComposePath): SkPath {
		val vB = PathBuilder()
		val vCommands = (inPath as? ProjectPath)?.commands
		if (vCommands == null) return vB.snapshot().also { vB.close() }
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
