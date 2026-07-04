package com.compose.desktop.native.renderer.sdl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.cinterop.*
import sdl3.*
import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: Sdl3Canvas
// ==================

/*
 Phase 9 B4 — SDL implementation of the upstream `androidx.compose.ui.graphics.Canvas`
 (the "android-side" backend, the SDL analogue of upstream's SkiaBackedCanvas). This is
 what the vendored `CanvasDrawScope` (inside `LayoutNodeDrawScope`) draws into once the
 composition pivots to upstream `LayoutNode`: `owner.root.draw(sdl3Canvas)` →
 `NodeCoordinator.draw` → `DrawModifierNode.draw(ContentDrawScope)` → `CanvasDrawScope` →
 here.

 Shape drawing reuses `Sdl3DrawScope`'s tessellation (the `*Core` helpers) verbatim — this
 canvas holds one `Sdl3DrawScope`, retargets its origin to the current transform before each
 call, and passes node-local coordinates. `Paint` is unpacked to a `Brush` + `DrawStyle` the
 cores already understand. State is a translate + clip stack (save/restore); the SDL vertex
 batch is flushed whenever the clip changes so batched geometry lands under the clip that was
 active when it was emitted.

 Not yet wired (later phases): scale / rotate / skew / concat (graphicsLayer transforms, B6);
 gradient shaders via `Paint.shader` (currently solid-colour only — B4 follow-up); drawImage /
 drawVertices / drawPoints (image leaf lands in B5). Those are no-ops / solid fallbacks for now.
*/
@OptIn(ExperimentalForeignApi::class)
internal class Sdl3Canvas(
	private val fRenderer: COpaquePointer,
	private val fSize: Size,
	private val fTextRenderer: Sdl3TextRenderer? = null,
	private val fImageCache: Sdl3ImageCache? = null,
) : Canvas,
	com.compose.desktop.native.text.NativeTextCanvas,
	com.compose.desktop.native.graphics.NativePainterCanvas {

	// The tessellating scope whose origin we retarget per draw call. Origin 0,0
	// initially; each draw sets it to the current translate (fTx, fTy).
	private val fScope = Sdl3DrawScope(fRenderer, 0f, 0f, fSize)

	// Current translate (accumulated Canvas.translate) in absolute pixel space.
	private var fTx: Float = 0f
	private var fTy: Float = 0f

	// Current clip (absolute px), or null for unclipped.
	private var fClip: IntArray? = null // [left, top, right, bottom]

	// save/restore stack of (tx, ty, clip).
	private val fStack = ArrayDeque<Triple<Float, Float, IntArray?>>()

	// Flushes any pending batched geometry to SDL, then frees the scope's
	// native buffer + clears the SDL clip. Call once per frame after the draw.
	fun finish() {
		fScope.release()
		SDL_SetRenderClipRect(fRenderer.reinterpret(), null)
	}

	// ============
	//  State (translate + clip stack)

	override fun save() {
		fStack.addLast(Triple(fTx, fTy, fClip))
	}

	override fun restore() {
		val vPrev = fStack.removeLastOrNull() ?: return
		fTx = vPrev.first
		fTy = vPrev.second
		if (vPrev.third !== fClip) {
			fScope.flush()
			fClip = vPrev.third
			applyClip()
		}
	}

	override fun saveLayer(bounds: Rect, paint: Paint) {
		// TODO(B6): real offscreen layer for alpha / blend. For now behaves as save().
		save()
	}

	override fun translate(dx: Float, dy: Float) {
		fTx += dx
		fTy += dy
	}

	// graphicsLayer transforms — deferred to B6. Positioning (translate) is
	// enough for the layout pipeline's box placement.
	override fun scale(sx: Float, sy: Float) {}
	override fun rotate(degrees: Float) {}
	override fun skew(sx: Float, sy: Float) {}
	override fun concat(matrix: Matrix) {}

	override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
		fScope.flush()
		val vNew = intArrayOf(
			(fTx + left).toInt(), (fTy + top).toInt(),
			(fTx + right).toInt(), (fTy + bottom).toInt(),
		)
		fClip = intersect(fClip, vNew)
		applyClip()
	}

	override fun clipPath(path: ComposePath, clipOp: ClipOp) {
		// SDL has only rectangular render clipping; approximate a path clip by
		// its bounding box (TODO: stencil/mask for non-rect clips).
		val vB = pathBounds(path) ?: return
		clipRect(vB[0], vB[1], vB[2], vB[3], clipOp)
	}

	private fun applyClip() {
		val vC = fClip
		if (vC == null) {
			SDL_SetRenderClipRect(fRenderer.reinterpret(), null)
			return
		}
		memScoped {
			val vRect = alloc<SDL_Rect>()
			vRect.x = vC[0]
			vRect.y = vC[1]
			vRect.w = max(0, vC[2] - vC[0])
			vRect.h = max(0, vC[3] - vC[1])
			// SDL copies the rect, so the memScoped allocation is safe to free after.
			SDL_SetRenderClipRect(fRenderer.reinterpret(), vRect.ptr)
		}
	}

	private fun intersect(inA: IntArray?, inB: IntArray): IntArray {
		if (inA == null) return inB
		return intArrayOf(
			max(inA[0], inB[0]), max(inA[1], inB[1]),
			min(inA[2], inB[2]), min(inA[3], inB[3]),
		)
	}

	// ============
	//  Primitives — retarget the scope origin to the current translate, then
	//  delegate to Sdl3DrawScope's tessellation with node-local coordinates.

	private fun prep(): Sdl3DrawScope {
		fScope.setOrigin(fTx, fTy)
		return fScope
	}

	// Solid colour only for now; gradient Paint.shader support is a B4 follow-up.
	private fun brushFor(inPaint: Paint): Brush = SolidColor(inPaint.color)

	private fun styleFor(inPaint: Paint): DrawStyle =
		if (inPaint.style == PaintingStyle.Stroke) Stroke(inPaint.strokeWidth, cap = inPaint.strokeCap)
		else Fill

	override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		prep().rectCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), paint.alpha, styleFor(paint))
	}

	override fun drawRoundRect(
		left: Float, top: Float, right: Float, bottom: Float,
		radiusX: Float, radiusY: Float, paint: Paint,
	) {
		prep().roundRectCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), radiusX, paint.alpha, styleFor(paint))
	}

	override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		prep().ovalCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), paint.alpha, styleFor(paint))
	}

	override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
		prep().circleCore(brushFor(paint), radius, center, paint.alpha, styleFor(paint))
	}

	override fun drawArc(
		left: Float, top: Float, right: Float, bottom: Float,
		startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint,
	) {
		prep().arcCore(brushFor(paint), startAngle, sweepAngle, useCenter, Offset(left, top), Size(right - left, bottom - top), paint.alpha, styleFor(paint))
	}

	override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
		prep().lineCore(brushFor(paint), p1, p2, paint.strokeWidth, paint.strokeCap, paint.alpha)
	}

	override fun drawPath(path: ComposePath, paint: Paint) {
		prep().pathCore(path, brushFor(paint), paint.alpha, styleFor(paint))
	}

	// ============
	//  Native text (B5) — bridge from a text DrawModifierNode. Flush pending
	//  tessellated geometry first so the text texture layers in the right z-order,
	//  then draw at the node's absolute origin (current translate + local offset).

	override fun drawNativeText(
		inText: String,
		inSpans: List<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>>?,
		inX: Float,
		inY: Float,
		inBoxWidth: Float,
		inBoxHeight: Float,
		inColor: androidx.compose.ui.graphics.Color,
		inFontSizePx: Int,
		inTextAlign: androidx.compose.ui.text.style.TextAlign,
		inSoftWrap: Boolean,
		inFontFamily: String?,
		inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>?,
	) {
		fScope.flush()
		fTextRenderer?.drawText(
			inText = inText,
			inX = (fTx + inX).toInt(),
			inY = (fTy + inY).toInt(),
			inBoxWidth = inBoxWidth.toInt(),
			inBoxHeight = inBoxHeight.toInt(),
			inColor = inColor,
			inFontSize = inFontSizePx,
			inAlign = inTextAlign,
			inFontFamily = inFontFamily,
			inFontVariations = inFontVariations,
			inSpans = inSpans,
			inTextStart = 0,
		)
	}

	// ============
	//  Native image (B5) — bridge from a painter DrawModifierNode. Flush pending
	//  geometry first (z-order), then blit via the decode cache at absolute origin.

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
		fScope.flush()
		fImageCache?.draw(
			inResourcePath, inKind,
			fTx + inX, fTy + inY, inWidth, inHeight,
			inContentScale, inAlpha,
		)
	}

	// ============
	//  Not-yet-wired ops (B5 image leaf / B6 layers). Accept-and-ignore.

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

	private fun pathBounds(inPath: ComposePath): FloatArray? {
		val vCommands = (inPath as? com.compose.desktop.native.graphics.ProjectPath)?.commands ?: return null
		if (vCommands.isEmpty()) return null
		var vMinX = Float.MAX_VALUE; var vMinY = Float.MAX_VALUE
		var vMaxX = -Float.MAX_VALUE; var vMaxY = -Float.MAX_VALUE
		fun acc(x: Float, y: Float) {
			vMinX = min(vMinX, x); vMinY = min(vMinY, y)
			vMaxX = max(vMaxX, x); vMaxY = max(vMaxY, y)
		}
		for (vCmd in vCommands) when (vCmd) {
			is com.compose.desktop.native.graphics.PathCommand.MoveTo -> acc(vCmd.x, vCmd.y)
			is com.compose.desktop.native.graphics.PathCommand.LineTo -> acc(vCmd.x, vCmd.y)
			is com.compose.desktop.native.graphics.PathCommand.QuadTo -> { acc(vCmd.cx, vCmd.cy); acc(vCmd.x, vCmd.y) }
			is com.compose.desktop.native.graphics.PathCommand.CubicTo -> { acc(vCmd.c1x, vCmd.c1y); acc(vCmd.c2x, vCmd.c2y); acc(vCmd.x, vCmd.y) }
			com.compose.desktop.native.graphics.PathCommand.Close -> {}
		}
		if (vMinX > vMaxX) return null
		return floatArrayOf(vMinX, vMinY, vMaxX, vMaxY)
	}
}
