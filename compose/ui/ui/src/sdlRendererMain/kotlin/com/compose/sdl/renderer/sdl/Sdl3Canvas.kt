package com.compose.sdl.renderer.sdl

import androidx.compose.ui.geometry.CornerRadius
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
import com.compose.sdl.graphics.a8
import com.compose.sdl.graphics.b8
import com.compose.sdl.graphics.g8
import com.compose.sdl.graphics.r8
import com.compose.sdl.icons.IconFont
import kotlinx.cinterop.*
import sdl3.*
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
	private val fShadowCache: Sdl3ShadowCache? = null,
	// Offscreen mode: this canvas renders into [fOffscreenTexture] (an ImageBitmap's
	// render target) instead of the frame. Set by Sdl3OffscreenRenderer so the vendored
	// VectorPainter / DrawCache pipeline works. fForceWhite draws every fill white (an
	// Alpha8 mask) so the tint applied on blit colours it correctly.
	private val fOffscreenTexture: COpaquePointer? = null,
	private val fForceWhite: Boolean = false,
	// Phase 4 capture mode: when set, this canvas RECORDS shape geometry into the list
	// (via the scope's recordingSink) and touches NO GPU. Ops not yet capturable
	// (text / image / clip / saveLayer / shadow) mark the list `unsupported` so the
	// SdlDisplayListRenderNode falls back to a crisp block-replay for that leaf — so
	// no un-captured op ever leaks to the screen. See SdlDisplayList.
	private val fCaptureList: SdlDisplayList? = null,
) : Canvas,
	com.compose.sdl.text.NativeTextCanvas,
	com.compose.sdl.graphics.NativePainterCanvas,
	com.compose.sdl.graphics.NativeShapeClipCanvas,
	com.compose.sdl.graphics.NativeShadowCanvas,
	com.compose.sdl.graphics.NativeFinishableCanvas {

	// The tessellating scope. It emits geometry in the canvas's user space; the
	// canvas hands it the current affine each draw (setMatrix) so scale / rotate /
	// translate reach the vertices.
	private val fScope = Sdl3DrawScope(fRenderer, 0f, 0f, fSize)

	init {
		// Capture mode routes the scope's geometry to the display list instead of the GPU.
		if (fCaptureList != null) fScope.recordingSink = fCaptureList
	}

	// Capture-mode gate for ops we can't record yet: flag the list unsupported (the
	// node will replay the block instead) and skip the GPU work. Returns true if the
	// caller should return early. No-op (false) outside capture mode.
	private fun captureUnsupported(): Boolean {
		val list = fCaptureList ?: return false
		list.unsupported = true
		return true
	}

	// Replay a captured display list into this (real) canvas under its CURRENT
	// transform, in order (z-order preserved). Geometry batches re-emit their
	// layer-local vertices through the affine; text runs flush pending geometry then
	// re-look-up + blit at the transformed origin (glyph size stays logical). Used by
	// SdlDisplayListRenderNode.drawInto.
	internal fun replayDisplayList(list: SdlDisplayList) {
		fScope.flush()
		fScope.setMatrix(fMa, fMb, fMc, fMd, fMe, fMf)
		for (i in list.commands.indices) {
			when (val cmd = list.commands[i]) {
				is GeometryBatch -> fScope.replayBatch(cmd.vertexData, cmd.vertexCount)
				is TextRun -> {
					fScope.flush() // submit geometry before this run (z-order)
					// Transform the run ORIGIN by the affine; keep glyph size logical
					// (matches the immediate path: layer scale reaches position, not size).
					fTextRenderer?.blitRun(
						inFontFamily = cmd.fontFamily,
						inText = cmd.text,
						inFontSize = cmd.fontSizePx,
						inFontVariations = cmd.variations,
						inStyle = cmd.style,
						inDeviceX = mapX(cmd.x, cmd.y),
						inDeviceY = mapY(cmd.x, cmd.y),
						inLogW = cmd.w,
						inLogH = cmd.h,
						inColor = androidx.compose.ui.graphics.Color(cmd.colorArgb),
					)
				}
				is IconRun -> {
					fScope.flush() // submit geometry before this glyph (z-order)
					// Map the box origin through the affine; box size stays logical (the
					// glyph centres in it — layer scale reaches position, not glyph size).
					fTextRenderer?.blitIconRun(
						inFontFamily = cmd.fontFamily,
						inText = cmd.text,
						inFontSize = cmd.fontSizePx,
						inFontVariations = cmd.variations,
						inDeviceBoxX = mapX(cmd.boxX, cmd.boxY),
						inDeviceBoxY = mapY(cmd.boxX, cmd.boxY),
						inBoxW = cmd.boxW,
						inBoxH = cmd.boxH,
						inColor = androidx.compose.ui.graphics.Color(cmd.colorArgb),
					)
				}
			}
		}
		fScope.flush()
	}

	// Current user→screen affine (a,b,c,d,e,f): screen = (a*x+c*y+e, b*x+d*y+f).
	// Accumulates Canvas.translate/scale/rotate/concat; identity + a translate is
	// the common layout-positioning case.
	private var fMa = 1f; private var fMb = 0f; private var fMc = 0f
	private var fMd = 1f; private var fMe = 0f; private var fMf = 0f

	private fun mapX(inX: Float, inY: Float): Float = fMa * inX + fMc * inY + fMe
	private fun mapY(inX: Float, inY: Float): Float = fMb * inX + fMd * inY + fMf

	// Right-multiply the affine by [na..nf] — applies the new transform in the
	// current user space, matching Canvas.translate/scale/rotate semantics.
	private fun concatMatrix(na: Float, nb: Float, nc: Float, nd: Float, ne: Float, nf: Float) {
		val a = fMa; val b = fMb; val c = fMc; val d = fMd; val e = fMe; val f = fMf
		fMa = a * na + c * nb
		fMb = b * na + d * nb
		fMc = a * nc + c * nd
		fMd = b * nc + d * nd
		fMe = a * ne + c * nf + e
		fMf = b * ne + d * nf + f
	}

	// Axis-aligned bounding box of a user-space rect after the affine (SDL clips
	// only to a rect; a rotated clip degrades to its AABB).
	private fun mapRectAABB(inL: Float, inT: Float, inR: Float, inB: Float): IntArray {
		val vX0 = mapX(inL, inT); val vX1 = mapX(inR, inT); val vX2 = mapX(inR, inB); val vX3 = mapX(inL, inB)
		val vY0 = mapY(inL, inT); val vY1 = mapY(inR, inT); val vY2 = mapY(inR, inB); val vY3 = mapY(inL, inB)
		return intArrayOf(
			minOf(vX0, vX1, vX2, vX3).toInt(), minOf(vY0, vY1, vY2, vY3).toInt(),
			maxOf(vX0, vX1, vX2, vX3).toInt(), maxOf(vY0, vY1, vY2, vY3).toInt(),
		)
	}

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
	private data class State(
		val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float,
		val clip: IntArray?, val alpha: Float, val clipLayers: Int, val pendingClips: Int,
	)
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
		val roundRect: RoundRect?,    // rounded outline (corner cutouts) — null for diff-rect mode
		val diffRect: IntArray? = null, // ClipOp.Difference: [l,t,r,b] to ZERO before compositing
	)
	private val fClipLayers = ArrayDeque<OffscreenClip>()

	// ============
	//  Lazy rounded clips. Opening an offscreen mask per clip is the SINGLE
	//  most expensive thing this canvas does (two render-target switches +
	//  clear + corner cut + feather + composite) - and the dominant Compose
	//  idiom `Modifier.clip(CircleShape).background(...)` never needs one:
	//  the background is a full-cover rect drawable AS the rounded shape, and
	//  content wholly inside the rounded region needs no mask at all. So
	//  clipRoundRect only RECORDS the clip; each draw either proves itself
	//  safe (containment / cover) or realizes the offscreen on demand.

	private class PendingRoundClip(
		val deviceRound: RoundRect,   // device-space outline (clamped radii)
		val localRound: RoundRect,    // as passed in (for cover -> shape-fill emission)
		val region: IntArray,         // fClip intersect bbox at push time
		val bbox: IntArray,           // device bbox of the outline
		val prevClip: IntArray?,      // fClip before this clip was pushed
		// Affine snapshot at push - cover -> fill re-emits in LOCAL space and
		// must bail if the transform moved since.
		val ma: Float, val mb: Float, val mc: Float, val md: Float, val me: Float, val mf: Float,
		// fStack.size when the clip was pushed: a lazily-realized mask must be
		// composited by the save-frame that OWNS the clip, not by whatever
		// inner frame happened to trigger realization (a press draws the state
		// layer and the ripple in separate inner clipRect{} frames - the first
		// realize must not let the inner restore close the mask, or the second
		// draw runs unmasked: the "square on click" artifact).
		val saveDepth: Int,
	)
	private val fPendingClips = ArrayDeque<PendingRoundClip>()

	/* Point-in-round-rect (device space) - inside the rect and, within a
	   corner cut square, inside its ellipse. */
	private fun roundRectContains(inRR: RoundRect, inX: Float, inY: Float): Boolean {
		if (inX < inRR.left || inX > inRR.right || inY < inRR.top || inY > inRR.bottom) return false
		fun corner(inRx: Float, inRy: Float, inDx: Float, inDy: Float): Boolean {
			if (inRx <= 0f || inRy <= 0f) return true
			val vNx = inDx / inRx; val vNy = inDy / inRy
			return vNx * vNx + vNy * vNy <= 1f
		}
		val vTl = inRR.topLeftCornerRadius; val vTr = inRR.topRightCornerRadius
		val vBr = inRR.bottomRightCornerRadius; val vBl = inRR.bottomLeftCornerRadius
		if (inX < inRR.left + vTl.x && inY < inRR.top + vTl.y)
			return corner(vTl.x, vTl.y, inX - (inRR.left + vTl.x), inY - (inRR.top + vTl.y))
		if (inX > inRR.right - vTr.x && inY < inRR.top + vTr.y)
			return corner(vTr.x, vTr.y, inX - (inRR.right - vTr.x), inY - (inRR.top + vTr.y))
		if (inX > inRR.right - vBr.x && inY > inRR.bottom - vBr.y)
			return corner(vBr.x, vBr.y, inX - (inRR.right - vBr.x), inY - (inRR.bottom - vBr.y))
		if (inX < inRR.left + vBl.x && inY > inRR.bottom - vBl.y)
			return corner(vBl.x, vBl.y, inX - (inRR.left + vBl.x), inY - (inRR.bottom - vBl.y))
		return true
	}

	/* A device-space aabb is inside the round rect iff its four corners are. */
	private fun roundRectContainsAabb(inRR: RoundRect, inL: Float, inT: Float, inR: Float, inB: Float): Boolean =
		roundRectContains(inRR, inL, inT) && roundRectContains(inRR, inR, inT) &&
			roundRectContains(inRR, inR, inB) && roundRectContains(inRR, inL, inB)

	/* Device aabb of a LOCAL rect under the current affine. */
	private fun deviceAabb(inL: Float, inT: Float, inR: Float, inB: Float): FloatArray {
		val vX0 = mapX(inL, inT); val vX1 = mapX(inR, inT); val vX2 = mapX(inR, inB); val vX3 = mapX(inL, inB)
		val vY0 = mapY(inL, inT); val vY1 = mapY(inR, inT); val vY2 = mapY(inR, inB); val vY3 = mapY(inL, inB)
		return floatArrayOf(
			minOf(vX0, vX1, vX2, vX3), minOf(vY0, vY1, vY2, vY3),
			maxOf(vX0, vX1, vX2, vX3), maxOf(vY0, vY1, vY2, vY3),
		)
	}

	/* If the LOCAL-space aabb stays inside every pending rounded clip, drawing
	   it under the already-active rect clip is correct - no mask needed.
	   Otherwise realize the pending masks before the draw proceeds. */
	private fun admitDraw(inL: Float, inT: Float, inR: Float, inB: Float) {
		if (fPendingClips.isEmpty()) return
		val vBox = deviceAabb(inL, inT, inR, inB)
		for (vPending in fPendingClips) {
			if (!roundRectContainsAabb(vPending.deviceRound, vBox[0], vBox[1], vBox[2], vBox[3])) {
				realizePendingClips()
				return
			}
		}
	}

	/* Convert pending clips into real offscreen mask layers (outermost first).

	   Each clip realizes with its OWN push-time region (pending.region =
	   enclosing-clip-at-push ∩ bbox), NOT the current fClip: a lazily-realized
	   clip may fire arbitrarily deep inside descendants whose own clips have
	   since narrowed fClip. Re-intersecting with that narrow clip shrank an
	   outer clip's offscreen to the innermost descendant's rect, so its
	   composite blitted back only that sliver and every sibling drawn into the
	   same offscreen was discarded (the TLS-chain regression: only the first
	   card + nothing below it, until a scroll shifted which card triggered
	   realization). The real (already-narrowed) drawing clip is preserved
	   across the loop and restored at the end, so subsequent draws into the
	   innermost target stay correctly clipped. */
	private fun realizePendingClips() {
		if (fPendingClips.isEmpty()) return
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		val vDrawClip = fClip
		while (fPendingClips.isNotEmpty()) {
			val vPending = fPendingClips.removeFirst()
			fScope.flush()
			val vRegion = vPending.region
			val vTarget = fClipTargets?.target(fClipLayers.size, fSize.width.toInt(), fSize.height.toInt())
			if (vTarget == null || vRegion[2] <= vRegion[0] || vRegion[3] <= vRegion[1]) {
				// Degrade to the rect clip that is already active.
				continue
			}
			val vPrevTarget = SDL_GetRenderTarget(vRenderer)
			SDL_SetRenderTarget(vRenderer, vTarget.reinterpret())
			fClip = vRegion
			applyClip()
			clearRegion(vRegion)
			com.compose.sdl.graphics.DrawStats.maskRealizations++
			fClipLayers.addLast(OffscreenClip(vTarget, vPrevTarget, vPending.prevClip, vRegion, vPending.bbox, vPending.deviceRound))
			// Attribute the realized mask to the save-frame that pushed the
			// clip: inner frames entered since must NOT composite it on their
			// restore - bump their thresholds past the new layer.
			for (vI in vPending.saveDepth until fStack.size) {
				val vState = fStack[vI]
				if (vState.clipLayers < fClipLayers.size) {
					fStack[vI] = vState.copy(clipLayers = fClipLayers.size)
				}
			}
		}
		// Restore the real drawing clip (already narrowed by inner clipRects /
		// scroll bounds) for draws into the now-active innermost target.
		fClip = vDrawClip
		applyClip()
	}

	// Flushes any pending batched geometry to SDL, then frees the scope's
	// native buffer + clears the SDL clip. Call once per frame after the draw.
	override fun finish() {
		fPendingClips.clear()
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
		// Offscreen canvas: the outermost save (CanvasDrawScope brackets its draw with
		// save/restore) switches the SDL render target to the ImageBitmap's texture.
		if (fOffscreenTexture != null && fStack.isEmpty()) beginOffscreen()
		fStack.addLast(State(fMa, fMb, fMc, fMd, fMe, fMf, fClip, fAlpha, fClipLayers.size, fPendingClips.size))
	}

	override fun restore() {
		val vPrev = fStack.removeLastOrNull() ?: return
		// Pending (never-realized) rounded clips opened inside this save frame:
		// nothing was drawn through an offscreen for them - just drop them.
		while (fPendingClips.size > vPrev.pendingClips) fPendingClips.removeLast()
		// Composite + pop any offscreen rounded clips opened inside this save frame
		// before restoring the plain state (each composite restores render target
		// and SDL clip to what it was when the clip was opened).
		while (fClipLayers.size > vPrev.clipLayers) {
			compositeClipLayer()
		}
		fMa = vPrev.a; fMb = vPrev.b; fMc = vPrev.c; fMd = vPrev.d; fMe = vPrev.e; fMf = vPrev.f
		if (vPrev.clip !== fClip) {
			fScope.flush()
			fClip = vPrev.clip
			applyClip()
		}
		fAlpha = vPrev.alpha
		// Matching outermost restore of an offscreen canvas: flush the vector into the
		// texture and hand the render target back to the frame.
		if (fOffscreenTexture != null && fStack.isEmpty()) endOffscreen()
	}

	// Public so an offscreen render can commit the frame's pending geometry before it
	// borrows the render target (keeps the icon above already-drawn content).
	fun flushPending() {
		fScope.flush()
	}

	// Render target this canvas draws its normal geometry into (the frame, or the
	// current rounded-clip scratch layer).
	private fun mainTarget(): COpaquePointer? = fClipLayers.lastOrNull()?.target

	private var fSavedOffscreenTarget: COpaquePointer? = null

	private fun beginOffscreen() {
		val vTex = fOffscreenTexture ?: return
		// Commit whatever the frame has drawn so far to its own target, THEN redirect.
		currentMainCanvas?.flushPending()
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		fSavedOffscreenTarget = SDL_GetRenderTarget(vRenderer)
		SDL_SetRenderTarget(vRenderer, vTex.reinterpret())
		// Clear to transparent. Render-target textures aren't auto-cleared, and a
		// retained-layer texture (SdlRenderNode) is REUSED across re-records — without
		// this, a re-record ghosts the previous content, which showed up as multi-frame
		// instability on churny screens (Pickers). Fresh textures happen to be zeroed
		// so static leaves were fine, but reuse needs an explicit clear. RenderClear
		// fills with the draw colour ignoring blend, so (0,0,0,0) = transparent.
		SDL_SetRenderDrawColor(vRenderer, 0u, 0u, 0u, 0u)
		SDL_RenderClear(vRenderer)
		fClip = null
		SDL_SetRenderClipRect(vRenderer, null)
	}

	private fun endOffscreen() {
		fScope.flush()
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		SDL_SetRenderTarget(vRenderer, fSavedOffscreenTarget?.reinterpret())
		fSavedOffscreenTarget = null
	}

	override fun saveLayer(bounds: Rect, paint: Paint) {
		if (captureUnsupported()) return
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

	override fun translate(dx: Float, dy: Float) = concatMatrix(1f, 0f, 0f, 1f, dx, dy)

	override fun scale(sx: Float, sy: Float) = concatMatrix(sx, 0f, 0f, sy, 0f, 0f)

	override fun rotate(degrees: Float) {
		val vRad = degrees * (PI / 180.0).toFloat()
		val vCos = cos(vRad); val vSin = sin(vRad)
		concatMatrix(vCos, vSin, -vSin, vCos, 0f, 0f)
	}

	override fun skew(sx: Float, sy: Float) = concatMatrix(1f, sy, sx, 1f, 0f, 0f)

	// Compose Matrix is a 4x4 column-major array; take the 2D affine components
	// (scaleX, skewY, skewX, scaleY, translateX, translateY).
	override fun concat(matrix: Matrix) {
		val v = matrix.values
		concatMatrix(v[0], v[1], v[4], v[5], v[12], v[13])
	}

	override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
		if (captureUnsupported()) return
		if (clipOp == ClipOp.Difference) {
			clipRectDifference(left, top, right, bottom)
			return
		}
		fScope.flush()
		fClip = intersect(fClip, mapRectAABB(left, top, right, bottom))
		applyClip()
	}

	// ClipOp.Difference — "draw everywhere EXCEPT this rect". SDL clips only to
	// a rect, so the subtree renders into an offscreen target and the excluded
	// rect is zeroed before compositing back on the matching restore(). This is
	// what cuts the floating-label notch out of OutlinedTextField's border —
	// treating Difference as intersect clipped the border TO the notch instead
	// (a floating line under the label, no outline anywhere else).
	private fun clipRectDifference(inL: Float, inT: Float, inR: Float, inB: Float) {
		realizePendingClips()
		val vDiff = mapRectAABB(inL, inT, inR, inB)
		val vRegion = fClip ?: intArrayOf(0, 0, fSize.width.toInt(), fSize.height.toInt())
		if (fClipTargets == null || fSize.width < 1f || fSize.height < 1f ||
			vRegion[2] <= vRegion[0] || vRegion[3] <= vRegion[1]
		) {
			// No offscreen pool — degrade by IGNORING the exclusion. Drawing the
			// full content (border passing under the label) is far less wrong
			// than clipping to the excluded rect.
			return
		}
		fScope.flush()
		val vTarget = fClipTargets.target(fClipLayers.size, fSize.width.toInt(), fSize.height.toInt()) ?: return
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		val vPrevTarget = SDL_GetRenderTarget(vRenderer)
		val vPrevClip = fClip
		SDL_SetRenderTarget(vRenderer, vTarget.reinterpret())
		fClip = vRegion
		applyClip()
		clearRegion(vRegion)
		fClipLayers.addLast(OffscreenClip(vTarget, vPrevTarget, vPrevClip, vRegion, vRegion, null, vDiff))
	}

	override fun clipPath(path: ComposePath, clipOp: ClipOp) {
		if (captureUnsupported()) return
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
		if (captureUnsupported()) return
		// The mask is cut in DEVICE space (the bbox below goes through the
		// affine), but the corner radii arrive in LOCAL user space — scale
		// them by the affine too, or a graphicsLayer-scaled circle clip cuts
		// with oversized corners and degenerates toward a lozenge (seen as
		// diamond-shaped "popped" bubbles under scale 0.55). Column norms
		// approximate per-axis scale; rotation already degrades to AABBs here.
		val vScaleX = sqrt(fMa * fMa + fMb * fMb)
		val vScaleY = sqrt(fMc * fMc + fMd * fMd)
		val vMaxRadius = maxOf(
			inRoundRect.topLeftCornerRadius.x * vScaleX, inRoundRect.topLeftCornerRadius.y * vScaleY,
			inRoundRect.topRightCornerRadius.x * vScaleX, inRoundRect.topRightCornerRadius.y * vScaleY,
			inRoundRect.bottomRightCornerRadius.x * vScaleX, inRoundRect.bottomRightCornerRadius.y * vScaleY,
			inRoundRect.bottomLeftCornerRadius.x * vScaleX, inRoundRect.bottomLeftCornerRadius.y * vScaleY,
		)
		// Effectively-square corners or no offscreen pool → plain rectangular clip.
		if (vMaxRadius < 0.5f || fClipTargets == null || fSize.width < 1f || fSize.height < 1f) {
			clipRect(inRoundRect.left, inRoundRect.top, inRoundRect.right, inRoundRect.bottom)
			return
		}
		fScope.flush()
		val vBbox = mapRectAABB(inRoundRect.left, inRoundRect.top, inRoundRect.right, inRoundRect.bottom)
		// Clamp to the DEVICE half-box: the bbox is int-truncated, so a scaled
		// radius can exceed it by a fraction — opposite corner cuts then overlap
		// and leave flat spots at N/E/S/W (a circle read as an octagon).
		val vHalfW = (vBbox[2] - vBbox[0]) / 2f
		val vHalfH = (vBbox[3] - vBbox[1]) / 2f
		fun deviceRadius(inR: CornerRadius) =
			CornerRadius(minOf(inR.x * vScaleX, vHalfW), minOf(inR.y * vScaleY, vHalfH))
		val vDeviceRound = RoundRect(
			vBbox[0].toFloat(), vBbox[1].toFloat(), vBbox[2].toFloat(), vBbox[3].toFloat(),
			topLeftCornerRadius = deviceRadius(inRoundRect.topLeftCornerRadius),
			topRightCornerRadius = deviceRadius(inRoundRect.topRightCornerRadius),
			bottomRightCornerRadius = deviceRadius(inRoundRect.bottomRightCornerRadius),
			bottomLeftCornerRadius = deviceRadius(inRoundRect.bottomLeftCornerRadius),
		)
		val vRegion = intersect(fClip, vBbox)
		// Nothing visible: keep behaviour of a normal clip (cull) without an
		// offscreen pass. The enclosing save/restore restores the clip afterwards.
		if (vRegion[2] <= vRegion[0] || vRegion[3] <= vRegion[1]) {
			fClip = vRegion
			applyClip()
			return
		}
		// LAZY: record only — the rect part of the clip applies immediately,
		// the offscreen mask is opened by the first draw that actually needs
		// it (admitDraw / realizePendingClips). The common clip+background
		// idiom never does.
		val vPrevClip = fClip
		fClip = vRegion
		applyClip()
		fPendingClips.addLast(PendingRoundClip(
			vDeviceRound, inRoundRect, vRegion, vBbox, vPrevClip,
			fMa, fMb, fMc, fMd, fMe, fMf,
			fStack.size,
		))
	}

	// Pops the top offscreen clip: flush its subtree, cut the rounded corners out
	// of the scratch, restore the previous target + clip, then blit the masked
	// region back onto the previous target (alpha-blended).
	private fun compositeClipLayer() {
		val vLayer = fClipLayers.removeLastOrNull() ?: return
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		fScope.flush()
		val vRound = vLayer.roundRect
		if (vRound != null) {
			zeroRoundRectCorners(vLayer.bbox, vRound)
		} else if (vLayer.diffRect != null) {
			// Difference clip: punch the excluded rect out of the scratch layer.
			val vCut = intersect(vLayer.diffRect, vLayer.region)
			if (vCut[2] > vCut[0] && vCut[3] > vCut[1]) clearRegion(vCut)
		}
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
		val vTl = inRoundRect.topLeftCornerRadius
		val vTr = inRoundRect.topRightCornerRadius
		val vBr = inRoundRect.bottomRightCornerRadius
		val vBl = inRoundRect.bottomLeftCornerRadius
		// Segments per quarter arc ~ every 4 device px along the arc, matching
		// Sdl3DrawScope's density (~64/full circle at typical radii).
		val vMaxR = maxOf(vTl.x, vTl.y, vTr.x, vTr.y, vBr.x, vBr.y, vBl.x, vBl.y)
		val vSeg = ((vMaxR * 1.5708f) / 4f).toInt().coerceIn(8, 32)
		fun cornerCount(inR: CornerRadius): Int = if (inR.x <= 0f || inR.y <= 0f) 0 else vSeg * 3
		val vFanMax = cornerCount(vTl) + cornerCount(vTr) + cornerCount(vBr) + cornerCount(vBl)
		if (vFanMax == 0) return
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		memScoped {
			// ============
			//  Pass 1 — hard cut: zero everything outside each corner arc
			//  (triangle fan from the corner apex, blend NONE writes RGBA 0).
			SDL_SetRenderDrawBlendMode(vRenderer, SDL_BLENDMODE_NONE)
			val vBuf = allocArray<SDL_Vertex>(vFanMax)
			var vIdx = 0
			fun put(inX: Float, inY: Float) {
				vBuf[vIdx].position.x = inX; vBuf[vIdx].position.y = inY
				vBuf[vIdx].color.r = 0f; vBuf[vIdx].color.g = 0f; vBuf[vIdx].color.b = 0f; vBuf[vIdx].color.a = 0f
				vBuf[vIdx].tex_coord.x = 0f; vBuf[vIdx].tex_coord.y = 0f
				vIdx++
			}
			fun cutout(inApexX: Float, inApexY: Float, inCx: Float, inCy: Float, inRx: Float, inRy: Float, inStartDeg: Float) {
				if (inRx <= 0f || inRy <= 0f) return
				var vPrevX = 0f; var vPrevY = 0f
				for (vI in 0..vSeg) {
					val vAngle = ((inStartDeg + 90f * vI / vSeg) * (PI / 180.0)).toFloat()
					val vPx = inCx + inRx * cos(vAngle)
					val vPy = inCy + inRy * sin(vAngle)
					if (vI > 0) {
						put(inApexX, inApexY)
						put(vPrevX, vPrevY)
						put(vPx, vPy)
					}
					vPrevX = vPx; vPrevY = vPy
				}
			}
			cutout(vLeft, vTop, vLeft + vTl.x, vTop + vTl.y, vTl.x, vTl.y, 180f)
			cutout(vRight, vTop, vRight - vTr.x, vTop + vTr.y, vTr.x, vTr.y, 270f)
			cutout(vRight, vBottom, vRight - vBr.x, vBottom - vBr.y, vBr.x, vBr.y, 0f)
			cutout(vLeft, vBottom, vLeft + vBl.x, vBottom - vBl.y, vBl.x, vBl.y, 90f)
			if (vIdx > 0) SDL_RenderGeometry(vRenderer, null, vBuf, vIdx, null, 0)

			// ============
			//  Pass 2 — feathered edge: a ~1.2px ring band straddling each arc
			//  whose vertex alpha ramps 1 → 0 outward, drawn with a custom
			//  "dst *= srcAlpha" blend. SDL_RenderGeometry has no antialiasing;
			//  without this the mask boundary is a hard polygon edge while the
			//  drawn shapes get Sdl3DrawScope's fringe AA (visibly rougher —
			//  scaled-down circle clips read as low-poly).
			val vMul = SDL_ComposeCustomBlendMode(
				SDL_BLENDFACTOR_ZERO, SDL_BLENDFACTOR_SRC_ALPHA, SDL_BLENDOPERATION_ADD,
				SDL_BLENDFACTOR_ZERO, SDL_BLENDFACTOR_SRC_ALPHA, SDL_BLENDOPERATION_ADD,
			)
			SDL_SetRenderDrawBlendMode(vRenderer, vMul)
			val vFeather = 1.2f
			val vBandMax = 4 * vSeg * 6
			val vBand = allocArray<SDL_Vertex>(vBandMax)
			var vBIdx = 0
			fun putBand(inX: Float, inY: Float, inKeep: Float) {
				vBand[vBIdx].position.x = inX; vBand[vBIdx].position.y = inY
				vBand[vBIdx].color.r = 1f; vBand[vBIdx].color.g = 1f; vBand[vBIdx].color.b = 1f; vBand[vBIdx].color.a = inKeep
				vBand[vBIdx].tex_coord.x = 0f; vBand[vBIdx].tex_coord.y = 0f
				vBIdx++
			}
			fun feather(inCx: Float, inCy: Float, inRx: Float, inRy: Float, inStartDeg: Float) {
				if (inRx <= 1f || inRy <= 1f) return
				var vPrevInX = 0f; var vPrevInY = 0f; var vPrevOutX = 0f; var vPrevOutY = 0f
				for (vI in 0..vSeg) {
					val vAngle = ((inStartDeg + 90f * vI / vSeg) * (PI / 180.0)).toFloat()
					val vCos = cos(vAngle); val vSin = sin(vAngle)
					val vInX = inCx + (inRx - vFeather) * vCos
					val vInY = inCy + (inRy - vFeather) * vSin
					val vOutX = inCx + inRx * vCos
					val vOutY = inCy + inRy * vSin
					if (vI > 0) {
						putBand(vPrevInX, vPrevInY, 1f); putBand(vPrevOutX, vPrevOutY, 0f); putBand(vInX, vInY, 1f)
						putBand(vInX, vInY, 1f); putBand(vPrevOutX, vPrevOutY, 0f); putBand(vOutX, vOutY, 0f)
					}
					vPrevInX = vInX; vPrevInY = vInY; vPrevOutX = vOutX; vPrevOutY = vOutY
				}
			}
			feather(vLeft + vTl.x, vTop + vTl.y, vTl.x, vTl.y, 180f)
			feather(vRight - vTr.x, vTop + vTr.y, vTr.x, vTr.y, 270f)
			feather(vRight - vBr.x, vBottom - vBr.y, vBr.x, vBr.y, 0f)
			feather(vLeft + vBl.x, vBottom - vBl.y, vBl.x, vBl.y, 90f)
			if (vBIdx > 0) SDL_RenderGeometry(vRenderer, null, vBand, vBIdx, null, 0)
		}
		SDL_SetRenderDrawBlendMode(vRenderer, SDL_BLENDMODE_BLEND)
	}

	// ============
	//  Primitives — retarget the scope origin to the current translate, then
	//  delegate to Sdl3DrawScope's tessellation with node-local coordinates.

	private fun prep(): Sdl3DrawScope {
		// Feed the scope the full affine; it emits geometry in user space and
		// transforms each vertex by this (origin stays 0 — the matrix carries the
		// translate).
		fScope.setMatrix(fMa, fMb, fMc, fMd, fMe, fMf)
		return fScope
	}

	// ============
	//  Drop shadow (NativeShadowCanvas) — the SDL renderer has no blur
	//  primitive, so the shadow is a stack of expanding, quadratically-spaced
	//  rounded-rect fills whose alphas accumulate: the region at distance d
	//  from the shape edge is covered by every ring inflated ≥ d, so coverage
	//  is maximal at the edge and steps down to zero at edge+blur. Each ring
	//  carries the tessellator's 1px AA fringe, which smooths the steps —
	//  at UI blur radii the result reads as a Gaussian. Content paints on
	//  top, hiding the (darkest) interior.

	override fun drawDropShadow(
		inOutline: androidx.compose.ui.graphics.Outline,
		inElevationPx: Float,
		inAmbientColor: androidx.compose.ui.graphics.Color,
		inSpotColor: androidx.compose.ui.graphics.Color,
	) {
		if (captureUnsupported()) return
		realizePendingClips()
		if (inElevationPx <= 0f) return

		// Generic paths (CutCornerShape, GenericShape) don't 9-slice — blur the
		// ACTUAL path mask once (Sdl3ShadowCache.getGeneric) and blit it. Falls
		// through to the bounds-rect approximation only when that fails.
		if (inOutline is androidx.compose.ui.graphics.Outline.Generic &&
			fShadowCache != null && fMb == 0f && fMc == 0f
		) {
			val vBlurG = inElevationPx.coerceAtLeast(1f)
			val vEntry = fShadowCache.getGeneric(inOutline.path, vBlurG.toInt().coerceAtLeast(1))
			if (vEntry != null) {
				val vAlphaG = 0.28f * inSpotColor.alpha * fAlpha
				val vOffY = inElevationPx * 0.4f
				fScope.flush()
				SDL_SetTextureColorMod(
					vEntry.tex.reinterpret(),
					inSpotColor.r8.toUByte(), inSpotColor.g8.toUByte(), inSpotColor.b8.toUByte(),
				)
				SDL_SetTextureAlphaMod(vEntry.tex.reinterpret(), (vAlphaG * 255f).toInt().coerceIn(0, 255).toUByte())
				memScoped {
					val vDst = alloc<SDL_FRect>()
					vDst.x = fMa * vEntry.originX + fMe
					vDst.y = fMd * (vEntry.originY + vOffY) + fMf
					vDst.w = fMa * vEntry.w
					vDst.h = fMd * vEntry.h
					SDL_RenderTexture(fRenderer.reinterpret(), vEntry.tex.reinterpret(), null, vDst.ptr)
				}
				return
			}
		}

		val vRect: Rect
		val vRadius: Float
		when (inOutline) {
			is androidx.compose.ui.graphics.Outline.Rectangle -> {
				vRect = inOutline.rect; vRadius = 0f
			}
			is androidx.compose.ui.graphics.Outline.Rounded -> {
				val vRr = inOutline.roundRect
				vRect = Rect(vRr.left, vRr.top, vRr.right, vRr.bottom)
				vRadius = vRr.topLeftCornerRadius.x
			}
			is androidx.compose.ui.graphics.Outline.Generic -> {
				vRect = inOutline.path.getBounds(); vRadius = 0f
			}
		}
		if (vRect.width <= 0f || vRect.height <= 0f) return

		// Material-ish geometry: blur radius ≈ elevation, spot offset pushes
		// the shadow down by a fraction of it.
		val vBlur = inElevationPx.coerceAtLeast(1f)
		val vOffsetY = inElevationPx * 0.4f
		val vMaxAlpha = 0.28f * inSpotColor.alpha * fAlpha

		// Fast path: one cached 9-slice tile blit (see Sdl3ShadowCache).
		// Requires an axis-aligned transform (no rotation/skew) and a shape
		// big enough that the four corner quadrants don't overlap.
		val vAxisAligned = fMb == 0f && fMc == 0f
		if (vAxisAligned && fShadowCache != null) {
			val vRadI = vRadius.toInt().coerceAtLeast(0)
			val vBlurI = vBlur.toInt().coerceAtLeast(1)
			val vEntry = fShadowCache.get(vRadI, vBlurI)
			val vCorner = vEntry?.corner?.toFloat() ?: 0f
			// Map the blur-inflated shadow rect through the (axis-aligned) affine.
			val vL = fMa * (vRect.left - vBlur) + fMe
			val vT = fMd * (vRect.top - vBlur + vOffsetY) + fMf
			val vRt = fMa * (vRect.right + vBlur) + fMe
			val vB = fMd * (vRect.bottom + vBlur + vOffsetY) + fMf
			val vScale = if (fMa > 0f) fMa else 1f
			if (vEntry != null && (vRt - vL) >= 2f * vCorner * vScale && (vB - vT) >= 2f * vCorner * vScale) {
				fScope.flush()  // z-order: pending geometry first
				SDL_SetTextureColorMod(
					vEntry.tex.reinterpret(),
					inSpotColor.r8.toUByte(), inSpotColor.g8.toUByte(), inSpotColor.b8.toUByte(),
				)
				SDL_SetTextureAlphaMod(vEntry.tex.reinterpret(), (vMaxAlpha * 255f).toInt().coerceIn(0, 255).toUByte())
				memScoped {
					val vDst = alloc<SDL_FRect>()
					vDst.x = vL; vDst.y = vT; vDst.w = vRt - vL; vDst.h = vB - vT
					SDL_RenderTexture9Grid(
						fRenderer.reinterpret(),
						vEntry.tex.reinterpret(),
						null,
						vCorner, vCorner, vCorner, vCorner,
						vScale,
						vDst.ptr,
					)
				}
				return
			}
		}

		// Fallback (rotated layers / tiny shapes / no cache): stacked rings.
		// Peak coverage at the shape edge; per-ring alpha so N stacked blends
		// accumulate to it: 1-(1-a)^N = max  →  a = 1-(1-max)^(1/N).
		// Ring count scales with the blur radius — large elevations need more
		// rings or the quadratic spacing shows visible stepping.
		val vRings = (vBlur / 4f).toInt().coerceIn(5, 12)
		val vRingAlpha = 1f - (1f - vMaxAlpha).pow(1f / vRings)
		val vBrush = SolidColor(inSpotColor.copy(alpha = 1f))
		val vScope = prep()
		for (vI in 0 until vRings) {
			// Quadratic spacing — rings bunch near the edge where a Gaussian
			// falls fastest.
			val vT = (vI + 1).toFloat() / vRings
			val vInflate = vBlur * vT * vT
			vScope.roundRectCore(
				brush = vBrush,
				topLeft = Offset(vRect.left - vInflate, vRect.top - vInflate + vOffsetY),
				size = Size(vRect.width + 2f * vInflate, vRect.height + 2f * vInflate),
				cornerRadius = vRadius + vInflate,
				alpha = vRingAlpha,
				style = Fill,
			)
		}
	}

	// Gradient brushes stash themselves on the Paint's shader (ShaderBrush.applyTo
	// otherwise loses everything but color=Black — see CanvasPaintActuals). Recover
	// the gradient so the tessellator's per-vertex sampler paints it; fall back to
	// the solid paint colour for plain fills.
	private fun brushFor(inPaint: Paint): Brush =
		if (fForceWhite) SolidColor(androidx.compose.ui.graphics.Color.White)
		else inPaint.shader?.brush ?: SolidColor(inPaint.color)

	private fun styleFor(inPaint: Paint): DrawStyle =
		if (inPaint.style == PaintingStyle.Stroke) Stroke(inPaint.strokeWidth, cap = inPaint.strokeCap)
		else Fill

	override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		// BlendMode.Clear zeroes the region (DrawCache clears an offscreen this way
		// before re-rasterising a vector). Write transparent directly instead of the
		// paint colour.
		if (paint.blendMode == BlendMode.Clear) {
			realizePendingClips()
			fScope.flush()
			clearRegion(intArrayOf(
				mapX(left, top).toInt(), mapY(left, top).toInt(),
				mapX(right, bottom).toInt(), mapY(right, bottom).toInt(),
			))
			return
		}
		if (fPendingClips.isNotEmpty() && tryDrawRectUnderPendingClips(left, top, right, bottom, paint)) return
		prep().rectCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), (paint.alpha * fAlpha), styleFor(paint))
	}

	/* The `clip(shape).background(color)` idiom: a FILL rect that fully covers
	   the innermost pending clip becomes a direct rounded-shape fill (with the
	   scope's fringe AA — no offscreen mask at all). A rect fully INSIDE every
	   pending clip draws as-is. Returns true when the draw has been emitted. */
	private fun tryDrawRectUnderPendingClips(inL: Float, inT: Float, inR: Float, inB: Float, inPaint: Paint): Boolean {
		val vBox = deviceAabb(inL, inT, inR, inB)
		val vInner = fPendingClips.last()
		// Cover case: fill paint, no stroke, rect covers the whole clip outline,
		// affine unchanged since the clip was pushed (local-space re-emission),
		// uniform corner radii (the scope's roundRectCore takes one radius), and
		// the clip outline itself contained in every OUTER pending clip.
		val vLr = vInner.localRound
		val vUniform = vLr.topLeftCornerRadius.x == vLr.topLeftCornerRadius.y &&
			vLr.topLeftCornerRadius == vLr.topRightCornerRadius &&
			vLr.topLeftCornerRadius == vLr.bottomRightCornerRadius &&
			vLr.topLeftCornerRadius == vLr.bottomLeftCornerRadius
		val vAffineUnchanged = vInner.ma == fMa && vInner.mb == fMb && vInner.mc == fMc &&
			vInner.md == fMd && vInner.me == fMe && vInner.mf == fMf
		if (inPaint.style == PaintingStyle.Fill && inPaint.blendMode == BlendMode.SrcOver &&
			vUniform && vAffineUnchanged &&
			vBox[0] <= vInner.bbox[0] && vBox[1] <= vInner.bbox[1] &&
			vBox[2] >= vInner.bbox[2] && vBox[3] >= vInner.bbox[3]
		) {
			var vOutersContain = true
			for (vPending in fPendingClips) {
				if (vPending === vInner) continue
				if (!roundRectContainsAabb(vPending.deviceRound, vInner.deviceRound.left, vInner.deviceRound.top, vInner.deviceRound.right, vInner.deviceRound.bottom)) {
					vOutersContain = false; break
				}
			}
			if (vOutersContain) {
				prep().roundRectCore(
					brushFor(inPaint),
					Offset(vLr.left, vLr.top), Size(vLr.width, vLr.height),
					vLr.topLeftCornerRadius.x,
					(inPaint.alpha * fAlpha), Fill,
				)
				return true
			}
		}
		// Containment: inside every pending outline → the active rect clip is enough.
		for (vPending in fPendingClips) {
			if (!roundRectContainsAabb(vPending.deviceRound, vBox[0], vBox[1], vBox[2], vBox[3])) {
				realizePendingClips()
				return false
			}
		}
		return false
	}

	override fun drawRoundRect(
		left: Float, top: Float, right: Float, bottom: Float,
		radiusX: Float, radiusY: Float, paint: Paint,
	) {
		admitDraw(left, top, right, bottom)
		prep().roundRectCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), radiusX, (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		admitDraw(left, top, right, bottom)
		prep().ovalCore(brushFor(paint), Offset(left, top), Size(right - left, bottom - top), (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
		admitDraw(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
		prep().circleCore(brushFor(paint), radius, center, (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawArc(
		left: Float, top: Float, right: Float, bottom: Float,
		startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint,
	) {
		realizePendingClips()
		prep().arcCore(brushFor(paint), startAngle, sweepAngle, useCenter, Offset(left, top), Size(right - left, bottom - top), (paint.alpha * fAlpha), styleFor(paint))
	}

	override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
		realizePendingClips()
		prep().lineCore(brushFor(paint), p1, p2, paint.strokeWidth, paint.strokeCap, (paint.alpha * fAlpha))
	}

	override fun drawPath(path: ComposePath, paint: Paint) {
		realizePendingClips()
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
		inBaseItalic: Boolean,
		inTextDecoration: androidx.compose.ui.text.style.TextDecoration?,
		inLineHeightPx: Float,
		inTrimFirstLine: Boolean,
	) {
		// Capture mode: icon-font glyphs (Material Symbols via FreeType) record as an
		// IconRun — flush pending geometry first for z-order, then capture the box in
		// LAYER-LOCAL device coords (replay maps it through the layer affine + re-draws
		// via FreeTypeIcons' cache). Plain/decorated AND spanned text ARE captured too
		// (runSink set below; each styled run becomes a TextRun) — only a run with a
		// SpanStyle.background still bails inside Sdl3TextRenderer.drawText.
		val vCaptureList = fCaptureList
		if (vCaptureList != null && inFontFamily != null && IconFont.isIconFamily(inFontFamily)) {
			fScope.flush()
			val vIconColor = if (fAlpha >= 1f) inColor else inColor.copy(alpha = inColor.alpha * fAlpha)
			val vArgb = (vIconColor.a8 shl 24) or (vIconColor.r8 shl 16) or (vIconColor.g8 shl 8) or vIconColor.b8
			vCaptureList.captureIconRun(
				inFontFamily, inText, inFontSizePx, inFontVariations,
				mapX(inX, inY), mapY(inX, inY), inBoxWidth, inBoxHeight, vArgb,
			)
			return
		}
		// Small centred labels (a popped bubble's "pop") usually sit fully
		// inside a rounded clip — gate on containment like other draws, with
		// a 2px margin for glyph overhang / AA bleed, instead of paying a
		// mask pass per text run.
		admitDraw(inX - 2f, inY - 2f, inX + inBoxWidth + 2f, inY + inBoxHeight + 2f)
		com.compose.sdl.graphics.DrawStats.textDraws++
		fScope.flush()
		// Paragraph-level decoration bits forwarded to every wrapped line.
		val vUnderline = inTextDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.Underline) == true
		val vLineThrough = inTextDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) == true
		// Fold the layer's alpha stack into the text colour; SDL3_ttf has no per-blit
		// alpha (SDL_SetTextureAlphaMod would work, but we bake it in here to keep
		// the renderer path simple).
		val vColor = if (fAlpha >= 1f) inColor else inColor.copy(alpha = inColor.alpha * fAlpha)
		val vTr = fTextRenderer ?: return
		val vRenderer = vTr.textMeasurer

		// Icon families are single-codepoint glyphs that never wrap — centre in the
		// node's real box. The per-line path below centres within a lineHeight band
		// (1.2 em for Material Symbols), taller than the size-clamped icon node,
		// which pushed every icon ~0.1 em below centre.
		// Text is rasterised at its LOGICAL font size (stable glyph-texture cache
		// + stable hinting) and blit 1:1 at the affine-mapped pen position; the
		// layer's scale reaches position but NOT glyph size. Re-rasterising at a
		// per-frame device size (an earlier attempt) made text under an animated
		// layer scale — every Material popup/dropdown enter transition
		// (Menu.kt scaleX/scaleY) — step through integer sizes with visible
		// size/weight jumps, since SDL3_ttf re-hints at each size. Smoothly
		// scaling glyphs would need blit-scaling a logical-size rasterisation
		// (JVM/Skia GPU-scales the layer) — a future text-renderer enhancement;
		// until then logical-size + no glyph scale is the stable choice.
		if (inFontFamily != null && IconFont.isIconFamily(inFontFamily)) {
			vTr.drawText(
				inText = inText,
				inX = mapX(inX, inY).toInt(),
				inY = mapY(inX, inY).toInt(),
				inBoxWidth = inBoxWidth.toInt(),
				inBoxHeight = inBoxHeight.toInt(),
				inColor = vColor,
				inFontSize = inFontSizePx,
				inAlign = inTextAlign,
				inFontFamily = inFontFamily,
				inFontVariations = inFontVariations,
				inSpans = inSpans,
				inTextStart = 0,
			)
			return
		}

		// Wrap first, then draw one line at a time. Sdl3TextRenderer.drawText assumes
		// the input is a SINGLE already-wrapped line; if we passed the raw multi-word
		// text it would just render it flat (skiko path used to do the same until it
		// was fixed to call inSoftWrap-driven wrap inside SkiaTextRenderer.drawText).
		// Result: text stayed one line at full length and clipped at the container's
		// right edge instead of wrapping when the sidebar was resized narrower.
		val vWrapWidth = if (inSoftWrap && inBoxWidth > 0f) inBoxWidth.toInt() else Int.MAX_VALUE
		val vWrapped = vRenderer.wrap(inText, inFontSizePx, vWrapWidth, inFontFamily, inFontVariations)
		// Line stacking mirrors SdlParagraph's per-mode model: compat trim (raw styles)
		// keeps the FIRST line at the tight font cell; M3's Trim.None (inTrimFirstLine =
		// false) makes every line the full band. Following lines always advance by the
		// band (inLineHeightPx) when supplied, else the font cell.
		val vFontCellH = vRenderer.lineHeight(inFontSizePx, inFontFamily, inFontVariations)
		val vAdvance = if (inLineHeightPx > 0f) inLineHeightPx else vFontCellH
		val vFirstLineH = if (inTrimFirstLine || inLineHeightPx <= 0f) vFontCellH else vAdvance
		// Per-run fontSize spans make a line's box the TALLEST run cell on it —
		// same styledLineCellHeight the paragraph measured with, so paint stacks
		// lines exactly where layout put them.
		val vMetricSpans = com.compose.sdl.text.spansAffectMetrics(inSpans)

		// Capture mode: route each plain line's run to the display list (spanned lines
		// bail inside drawText via markUnsupported). Reset after the loop. No-op when
		// not capturing (vCaptureList == null).
		fTextRenderer?.runSink = vCaptureList

		var vLineY = inY
		for ((vIdx, vLine) in vWrapped.lines.withIndex()) {
			val vLineH =
				if (vMetricSpans && inSpans != null) {
					// Size spans can exceed the band, never shrink below it — the same
					// max() the paragraph's lineHeights used, so paint tracks layout
					// (first line keeps its tight cell under compat trim).
					kotlin.math.max(
						com.compose.sdl.text.styledLineCellHeight(
							vLine, vWrapped.lineStarts.getOrElse(vIdx) { 0 }, inSpans,
							inFontSizePx, vTr.dpr, vRenderer, inFontFamily, inFontVariations,
						),
						if ((vIdx == 0 && inTrimFirstLine) || inLineHeightPx <= 0f) 0f else inLineHeightPx,
					)
				} else if (vIdx == 0) vFirstLineH else vAdvance
			// Cull lines that would fall entirely below the box (softWrap keeps the
			// natural line count; the box just clips at draw time).
			if (vLineY >= inY + inBoxHeight) break
			vTr.drawText(
				inText = vLine,
				// Position maps through the affine; size/boxes switch to DEVICE
				// units here (scaled by the affine's column norms) so glyphs
				// scale with the layer. Rotation still only repositions.
				inX = mapX(inX, vLineY).toInt(),
				inY = mapY(inX, vLineY).toInt(),
				inBoxWidth = inBoxWidth.toInt(),
				inBoxHeight = vLineH.toInt(),
				inColor = vColor,
				inFontSize = inFontSizePx,
				inAlign = inTextAlign,
				inFontFamily = inFontFamily,
				inFontVariations = inFontVariations,
				inSpans = inSpans,
				inTextStart = vWrapped.lineStarts.getOrElse(vIdx) { 0 },
				inItalic = inBaseItalic,
				inUnderline = vUnderline,
				inLineThrough = vLineThrough,
			)
			vLineY += vLineH
		}
		fTextRenderer?.runSink = null
	}

	// ============
	//  Native image (B5) — bridge from a painter DrawModifierNode. Flush pending
	//  geometry first (z-order), then blit via the decode cache at absolute origin.

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
		if (captureUnsupported()) return
		admitDraw(inX, inY, inX + inWidth, inY + inHeight)
		fScope.flush()
		// Map the origin through the affine and scale the size by the matrix's axis
		// magnitudes (rotation contributes 1, so it only repositions the blit — the
		// image itself isn't rotated, since SDL_RenderTexture is axis-aligned).
		val vSx = kotlin.math.sqrt(fMa * fMa + fMb * fMb)
		val vSy = kotlin.math.sqrt(fMc * fMc + fMd * fMd)
		fImageCache?.draw(
			inResourcePath, inKind,
			mapX(inX, inY), mapY(inX, inY), inWidth * vSx, inHeight * vSy,
			inContentScale, inAlpha * fAlpha,
		)
	}

	// ============
	//  Not-yet-wired ops (B5 image leaf / B6 layers). Accept-and-ignore.

	override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) {
		if (captureUnsupported()) return
		val vBmp = image as? SdlImageBitmap ?: return
		admitDraw(topLeftOffset.x, topLeftOffset.y, topLeftOffset.x + vBmp.width, topLeftOffset.y + vBmp.height)
		drawImageRect(
			image,
			androidx.compose.ui.unit.IntOffset.Zero,
			androidx.compose.ui.unit.IntSize(vBmp.width, vBmp.height),
			androidx.compose.ui.unit.IntOffset(topLeftOffset.x.toInt(), topLeftOffset.y.toInt()),
			androidx.compose.ui.unit.IntSize(vBmp.width, vBmp.height),
			paint,
		)
	}

	// Blit an ImageBitmap-backed texture (a rasterised vector: material icons etc.).
	// The tint an Icon requests arrives as paint.colorFilter (ColorFilter.tint); SDL
	// applies it as a texture colour-mod, which — because the mask was drawn WHITE
	// (see fForceWhite) — recolours the covered pixels to the tint.
	override fun drawImageRect(
		image: ImageBitmap,
		srcOffset: androidx.compose.ui.unit.IntOffset,
		srcSize: androidx.compose.ui.unit.IntSize,
		dstOffset: androidx.compose.ui.unit.IntOffset,
		dstSize: androidx.compose.ui.unit.IntSize,
		paint: Paint,
	) {
		if (captureUnsupported()) return
		realizePendingClips()
		val vTex = (image as? SdlImageBitmap)?.texture ?: return
		com.compose.sdl.graphics.DrawStats.imageBlits++
		// Commit pending frame geometry and re-assert this canvas's target + clip
		// (an offscreen render just borrowed the render target).
		fScope.flush()
		val vRenderer = fRenderer.reinterpret<cnames.structs.SDL_Renderer>()
		SDL_SetRenderTarget(vRenderer, mainTarget()?.reinterpret())
		applyClip()

		val vTint = (paint.colorFilter as? androidx.compose.ui.graphics.BlendModeColorFilter)?.color
		val vAlpha = (paint.alpha * fAlpha).coerceIn(0f, 1f)
		if (vTint != null) {
			SDL_SetTextureColorMod(vTex.reinterpret(), vTint.r8.toUByte(), vTint.g8.toUByte(), vTint.b8.toUByte())
			SDL_SetTextureAlphaMod(vTex.reinterpret(), (vTint.alpha * vAlpha * 255f).toInt().coerceIn(0, 255).toUByte())
		} else {
			SDL_SetTextureColorMod(vTex.reinterpret(), 255u, 255u, 255u)
			SDL_SetTextureAlphaMod(vTex.reinterpret(), (vAlpha * 255f).toInt().coerceIn(0, 255).toUByte())
		}
		SDL_SetTextureBlendMode(vTex.reinterpret(), SDL_BLENDMODE_BLEND_PREMULTIPLIED)

		val vDl = dstOffset.x.toFloat(); val vDt = dstOffset.y.toFloat()
		val vDr = vDl + dstSize.width; val vDb = vDt + dstSize.height
		val vX0 = mapX(vDl, vDt); val vX1 = mapX(vDr, vDb)
		val vY0 = mapY(vDl, vDt); val vY1 = mapY(vDr, vDb)
		memScoped {
			val vSrc = alloc<SDL_FRect>()
			vSrc.x = srcOffset.x.toFloat(); vSrc.y = srcOffset.y.toFloat()
			vSrc.w = srcSize.width.toFloat(); vSrc.h = srcSize.height.toFloat()
			val vDst = alloc<SDL_FRect>()
			vDst.x = min(vX0, vX1); vDst.y = min(vY0, vY1)
			vDst.w = kotlin.math.abs(vX1 - vX0); vDst.h = kotlin.math.abs(vY1 - vY0)
			SDL_RenderTexture(vRenderer, vTex.reinterpret(), vSrc.ptr, vDst.ptr)
		}
	}
	override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) {}
	override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) {}
	override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) {}
	override fun enableZ() {}
	override fun disableZ() {}

	private fun pathBounds(inPath: ComposePath): FloatArray? {
		val vCommands = (inPath as? com.compose.sdl.graphics.ProjectPath)?.commands ?: return null
		if (vCommands.isEmpty()) return null
		var vMinX = Float.MAX_VALUE; var vMinY = Float.MAX_VALUE
		var vMaxX = -Float.MAX_VALUE; var vMaxY = -Float.MAX_VALUE
		fun acc(x: Float, y: Float) {
			vMinX = min(vMinX, x); vMinY = min(vMinY, y)
			vMaxX = max(vMaxX, x); vMaxY = max(vMaxY, y)
		}
		for (vCmd in vCommands) when (vCmd) {
			is com.compose.sdl.graphics.PathCommand.MoveTo -> acc(vCmd.x, vCmd.y)
			is com.compose.sdl.graphics.PathCommand.LineTo -> acc(vCmd.x, vCmd.y)
			is com.compose.sdl.graphics.PathCommand.QuadTo -> { acc(vCmd.cx, vCmd.cy); acc(vCmd.x, vCmd.y) }
			is com.compose.sdl.graphics.PathCommand.CubicTo -> { acc(vCmd.c1x, vCmd.c1y); acc(vCmd.c2x, vCmd.c2y); acc(vCmd.x, vCmd.y) }
			com.compose.sdl.graphics.PathCommand.Close -> {}
		}
		if (vMinX > vMaxX) return null
		return floatArrayOf(vMinX, vMinY, vMaxX, vMaxY)
	}
}
