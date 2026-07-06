package com.compose.desktop.native.renderer.sdl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
	private val fClipTargets: Sdl3ClipTargets? = null,
) : Canvas,
	com.compose.desktop.native.text.NativeTextCanvas,
	com.compose.desktop.native.graphics.NativePainterCanvas,
	com.compose.desktop.native.graphics.NativeShapeClipCanvas {

	// The tessellating scope whose origin we retarget per draw call. Origin 0,0
	// initially; each draw sets it to the current translate (fTx, fTy).
	private val fScope = Sdl3DrawScope(fRenderer, 0f, 0f, fSize)

	// Current translate (accumulated Canvas.translate) in absolute pixel space.
	private var fTx: Float = 0f
	private var fTy: Float = 0f

	// Current clip (absolute px), or null for unclipped.
	private var fClip: IntArray? = null // [left, top, right, bottom]

	// Current alpha multiplier (product of every enclosing saveLayer's (paint.alpha * fAlpha)).
	// SDL3 has no offscreen-layer compositing, so alpha propagates by multiplication
	// through every primitive draw. Correct as long as painted shapes don't overlap
	// within a layer (which is the normal case for a graphicsLayer(alpha=…) block).
	private var fAlpha: Float = 1f

	// save/restore stack of (tx, ty, clip, alpha, clipLayers). `clipLayers` is the
	// offscreen-clip stack depth at save() time; restore() composites+pops any
	// offscreen clip opened inside this save frame (see fClipLayers below).
	private data class State(val tx: Float, val ty: Float, val clip: IntArray?, val alpha: Float, val clipLayers: Int)
	private val fStack = ArrayDeque<State>()

	// Stack of active rounded-shape clips, each backed by an offscreen render
	// target (see clipRoundRect / compositeClipLayer). SDL only clips to a rect,
	// so a rounded clip draws its subtree into an offscreen target, zeroes the
	// pixels outside the rounded outline, then composites the target back on the
	// matching restore().
	private class OffscreenClip(
		val target: COpaquePointer,   // scratch render target this clip draws into
		val prevTarget: COpaquePointer?, // render target active before this clip (null = window)
		val prevClip: IntArray?,      // SDL clip rect active before this clip
		val region: IntArray,         // [l,t,r,b] absolute px actually cleared + composited back
		val bbox: IntArray,           // [l,t,r,b] absolute px of the full rounded rect (corner math)
		val roundRect: RoundRect,     // the rounded outline (local coords, for corner radii)
	)
	private val fClipLayers = ArrayDeque<OffscreenClip>()

	// Flushes any pending batched geometry to SDL, then frees the scope's
	// native buffer + clears the SDL clip. Call once per frame after the draw.
	fun finish() {
		fScope.release()
		// Safety: an unbalanced save/restore must never leave the renderer pointed
		// at a scratch target when the frame is presented.
		if (fClipLayers.isNotEmpty()) {
			SDL_SetRenderTarget(fRenderer.reinterpret(), null)
			fClipLayers.clear()
		}
		SDL_SetRenderClipRect(fRenderer.reinterpret(), null)
	}

	// ============
	//  State (translate + clip stack)

	override fun save() {
		fStack.addLast(State(fTx, fTy, fClip, fAlpha, fClipLayers.size))
	}

	override fun restore() {
		val vPrev = fStack.removeLastOrNull() ?: return
		// Composite + pop any offscreen rounded clips opened inside this save frame
		// before restoring the plain state (each composite restores render target
		// and SDL clip to what it was when the clip was opened).
		while (fClipLayers.size > vPrev.clipLayers) {
			compositeClipLayer()
		}
		fTx = vPrev.tx
		fTy = vPrev.ty
		if (vPrev.clip !== fClip) {
			fScope.flush()
			fClip = vPrev.clip
			applyClip()
		}
		fAlpha = vPrev.alpha
	}

	override fun saveLayer(bounds: Rect, paint: Paint) {
		// No real offscreen buffer — SDL3 lacks a portable render-target-in-a-batched-scope
		// primitive here. Instead, multiplicatively propagate the layer's alpha through
		// every enclosed primitive: each draw() reads `fAlpha` and multiplies against
		// (paint.alpha * fAlpha). Restore() pops the multiplier back. Correct for non-overlapping
		// content (the common case for a `Modifier.graphicsLayer(alpha=…)` wrapping a
		// simple widget); overlapping shapes composite at the paint level rather than
		// at the layer level, which can produce slight visual differences from Skia.
		save()
		fAlpha *= paint.alpha
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
	//  Rounded-shape clip (NativeShapeClipCanvas) — SDL only clips to a rect, so
	//  a rounded / circular clip renders its subtree into an offscreen target,
	//  zeroes the pixels outside the rounded outline (blend NONE writes 0), then
	//  composites the target back on the matching restore(). Only reached for
	//  RoundedCornerShape / CircleShape (Outline.Rounded); rects use clipRect and
	//  arbitrary paths keep the clipPath bbox fallback.

	override fun clipRoundRect(inRoundRect: RoundRect) {
		val vMaxRadius = maxOf(
			inRoundRect.topLeftCornerRadius.x, inRoundRect.topLeftCornerRadius.y,
			inRoundRect.topRightCornerRadius.x, inRoundRect.topRightCornerRadius.y,
			inRoundRect.bottomRightCornerRadius.x, inRoundRect.bottomRightCornerRadius.y,
			inRoundRect.bottomLeftCornerRadius.x, inRoundRect.bottomLeftCornerRadius.y,
		)
		// Effectively-square corners or no offscreen pool → plain rectangular clip.
		if (vMaxRadius < 0.5f || fClipTargets == null || fSize.width < 1f || fSize.height < 1f) {
			clipRect(inRoundRect.left, inRoundRect.top, inRoundRect.right, inRoundRect.bottom)
			return
		}
		fScope.flush()
		val vBbox = intArrayOf(
			(fTx + inRoundRect.left).toInt(), (fTy + inRoundRect.top).toInt(),
			(fTx + inRoundRect.right).toInt(), (fTy + inRoundRect.bottom).toInt(),
		)
		val vRegion = intersect(fClip, vBbox)
		// Nothing visible: keep behaviour of a normal clip (cull) without an
		// offscreen pass. The enclosing save/restore restores the clip afterwards.
		if (vRegion[2] <= vRegion[0] || vRegion[3] <= vRegion[1]) {
			fClip = vRegion
			applyClip()
			return
		}
		val vTarget = fClipTargets.target(fClipLayers.size, fSize.width.toInt(), fSize.height.toInt())
		if (vTarget == null) {
			// Couldn't allocate a scratch target — degrade to a rectangular clip.
			clipRect(inRoundRect.left, inRoundRect.top, inRoundRect.right, inRoundRect.bottom)
			return
		}
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		val vPrevTarget = SDL_GetRenderTarget(vRenderer)
		val vPrevClip = fClip
		SDL_SetRenderTarget(vRenderer, vTarget.reinterpret())
		fClip = vRegion
		applyClip()
		clearRegion(vRegion)
		fClipLayers.addLast(OffscreenClip(vTarget, vPrevTarget, vPrevClip, vRegion, vBbox, inRoundRect))
	}

	// Pops the top offscreen clip: flush its subtree, cut the rounded corners out
	// of the scratch, restore the previous target + clip, then blit the masked
	// region back onto the previous target (alpha-blended).
	private fun compositeClipLayer() {
		val vLayer = fClipLayers.removeLastOrNull() ?: return
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		fScope.flush()
		zeroRoundRectCorners(vLayer.bbox, vLayer.roundRect)
		SDL_SetRenderTarget(vRenderer, vLayer.prevTarget?.reinterpret())
		fClip = vLayer.prevClip
		applyClip()
		blitRegion(vLayer.target, vLayer.region)
	}

	// Clears [inRect] on the current target to fully transparent (blend NONE
	// writes src directly, so it zeroes the region's RGBA).
	private fun clearRegion(inRect: IntArray) {
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		SDL_SetRenderDrawBlendMode(vRenderer, SDL_BLENDMODE_NONE)
		SDL_SetRenderDrawColor(vRenderer, 0u, 0u, 0u, 0u)
		memScoped {
			val vFRect = alloc<SDL_FRect>()
			vFRect.x = inRect[0].toFloat()
			vFRect.y = inRect[1].toFloat()
			vFRect.w = (inRect[2] - inRect[0]).toFloat()
			vFRect.h = (inRect[3] - inRect[1]).toFloat()
			SDL_RenderFillRect(vRenderer, vFRect.ptr)
		}
		SDL_SetRenderDrawBlendMode(vRenderer, SDL_BLENDMODE_BLEND)
	}

	// Blits [inRegion] of [inTarget] back onto the current render target 1:1
	// (render scale is 1, so absolute px map directly to target texels).
	private fun blitRegion(inTarget: COpaquePointer, inRegion: IntArray) {
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		memScoped {
			val vSrc = alloc<SDL_FRect>()
			val vDst = alloc<SDL_FRect>()
			vSrc.x = inRegion[0].toFloat(); vSrc.y = inRegion[1].toFloat()
			vSrc.w = (inRegion[2] - inRegion[0]).toFloat(); vSrc.h = (inRegion[3] - inRegion[1]).toFloat()
			vDst.x = vSrc.x; vDst.y = vSrc.y; vDst.w = vSrc.w; vDst.h = vSrc.h
			SDL_RenderTexture(vRenderer, inTarget.reinterpret(), vSrc.ptr, vDst.ptr)
		}
	}

	// Overwrites the four corner regions that fall OUTSIDE the rounded outline
	// with transparent pixels (blend NONE). Each corner is a triangle fan from
	// the bounding-box corner to the quarter-ellipse arc, so the fan covers
	// exactly "corner square minus rounded corner". Circles/pills fall out of
	// this too (radius == half-size makes the four arcs meet).
	private fun zeroRoundRectCorners(inBbox: IntArray, inRoundRect: RoundRect) {
		val vLeft = inBbox[0].toFloat(); val vTop = inBbox[1].toFloat()
		val vRight = inBbox[2].toFloat(); val vBottom = inBbox[3].toFloat()
		val vSeg = 12
		// Collect triangle vertices (x, y) for all four corner cutouts.
		val vVerts = ArrayList<Float>()
		fun cutout(inApexX: Float, inApexY: Float, inCx: Float, inCy: Float, inRx: Float, inRy: Float, inStartDeg: Float) {
			if (inRx <= 0f || inRy <= 0f) return
			var vPrevX = 0f; var vPrevY = 0f
			for (vI in 0..vSeg) {
				val vAngle = ((inStartDeg + 90f * vI / vSeg) * (PI / 180.0)).toFloat()
				val vPx = inCx + inRx * cos(vAngle)
				val vPy = inCy + inRy * sin(vAngle)
				if (vI > 0) {
					vVerts.add(inApexX); vVerts.add(inApexY)
					vVerts.add(vPrevX); vVerts.add(vPrevY)
					vVerts.add(vPx); vVerts.add(vPy)
				}
				vPrevX = vPx; vPrevY = vPy
			}
		}
		val vTl = inRoundRect.topLeftCornerRadius
		val vTr = inRoundRect.topRightCornerRadius
		val vBr = inRoundRect.bottomRightCornerRadius
		val vBl = inRoundRect.bottomLeftCornerRadius
		cutout(vLeft, vTop, vLeft + vTl.x, vTop + vTl.y, vTl.x, vTl.y, 180f)
		cutout(vRight, vTop, vRight - vTr.x, vTop + vTr.y, vTr.x, vTr.y, 270f)
		cutout(vRight, vBottom, vRight - vBr.x, vBottom - vBr.y, vBr.x, vBr.y, 0f)
		cutout(vLeft, vBottom, vLeft + vBl.x, vBottom - vBl.y, vBl.x, vBl.y, 90f)
		val vCount = vVerts.size / 2
		if (vCount == 0) return
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		SDL_SetRenderDrawBlendMode(vRenderer, SDL_BLENDMODE_NONE)
		memScoped {
			val vBuf = allocArray<SDL_Vertex>(vCount)
			for (vI in 0 until vCount) {
				vBuf[vI].position.x = vVerts[vI * 2]
				vBuf[vI].position.y = vVerts[vI * 2 + 1]
				vBuf[vI].color.r = 0f; vBuf[vI].color.g = 0f; vBuf[vI].color.b = 0f; vBuf[vI].color.a = 0f
				vBuf[vI].tex_coord.x = 0f; vBuf[vI].tex_coord.y = 0f
			}
			SDL_RenderGeometry(vRenderer, null, vBuf, vCount, null, 0)
		}
		SDL_SetRenderDrawBlendMode(vRenderer, SDL_BLENDMODE_BLEND)
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
		prep().rectCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawRoundRect(
		left: Float, top: Float, right: Float, bottom: Float,
		radiusX: Float, radiusY: Float, paint: Paint,
	) {
		prep().roundRectCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), radiusX, (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		prep().ovalCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
		prep().circleCore(brushFor(paint), radius, center, (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawArc(
		left: Float, top: Float, right: Float, bottom: Float,
		startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint,
	) {
		prep().arcCore(brushFor(paint), startAngle, sweepAngle, useCenter, Offset(left, top), Size(right - left, bottom - top), (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
		prep().lineCore(brushFor(paint), p1, p2, paint.strokeWidth, paint.strokeCap, (paint.alpha * fAlpha))
	}

	override fun drawPath(path: ComposePath, paint: Paint) {
		prep().pathCore(path, brushFor(paint), (paint.alpha * fAlpha), styleFor(paint))
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
		// Fold the layer's alpha stack into the text colour; SDL3_ttf has no per-blit
		// alpha (SDL_SetTextureAlphaMod would work, but we bake it in here to keep
		// the renderer path simple).
		val vColor = if (fAlpha >= 1f) inColor else inColor.copy(alpha = inColor.alpha * fAlpha)
		val vTr = fTextRenderer ?: return
		val vRenderer = vTr.textMeasurer

		// Wrap first, then draw one line at a time. Sdl3TextRenderer.drawText assumes
		// the input is a SINGLE already-wrapped line; if we passed the raw multi-word
		// text it would just render it flat (skiko path used to do the same until it
		// was fixed to call inSoftWrap-driven wrap inside SkiaTextRenderer.drawText).
		// Result: text stayed one line at full length and clipped at the container's
		// right edge instead of wrapping when the sidebar was resized narrower.
		val vWrapWidth = if (inSoftWrap && inBoxWidth > 0f) inBoxWidth.toInt() else Int.MAX_VALUE
		val vWrapped = vRenderer.wrap(inText, inFontSizePx, vWrapWidth, inFontFamily, inFontVariations)
		val vLineH = vRenderer.lineHeight(inFontSizePx, inFontFamily, inFontVariations)

		for ((vIdx, vLine) in vWrapped.lines.withIndex()) {
			val vLineY = inY + vIdx * vLineH
			// Cull lines that would fall entirely below the box (softWrap keeps the
			// natural line count; the box just clips at draw time).
			if (vLineY >= inY + inBoxHeight) break
			vTr.drawText(
				inText = vLine,
				inX = (fTx + inX).toInt(),
				inY = (fTy + vLineY).toInt(),
				inBoxWidth = inBoxWidth.toInt(),
				inBoxHeight = vLineH.toInt(),
				inColor = vColor,
				inFontSize = inFontSizePx,
				inAlign = inTextAlign,
				inFontFamily = inFontFamily,
				inFontVariations = inFontVariations,
				inSpans = inSpans,
				inTextStart = vWrapped.lineStarts.getOrElse(vIdx) { 0 },
			)
		}
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
			inContentScale, inAlpha * fAlpha,
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
