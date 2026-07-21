package com.compose.sdl.renderer.sdl

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.DefaultCameraDistance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.compose.sdl.graphics.DrawStats
import com.compose.sdl.graphics.NativeFinishableCanvas
import com.compose.sdl.graphics.NativeRenderNode
import com.compose.sdl.graphics.NativeShadowCanvas
import com.compose.sdl.graphics.NativeShapeClipCanvas
import com.compose.sdl.graphics.offscreenRenderer
import com.compose.sdl.graphics.prepareLayerTransformationMatrix

// ==================
// MARK: SdlDisplayListRenderNode — cached-GEOMETRY retained node (the default)
// ==================

/**
 The robust retained node. record() captures a leaf's tessellated geometry ONCE (in
 layer-local space, via a capture canvas that touches no GPU); drawInto() re-emits
 those cached vertices through the layer transform — crisp under ANY transform
 (vertices re-transformed, not a resampled texture), bit-exact (no texture round-trip),
 and with NO per-frame render-target state (so none of the texture node's timing-
 dependent nondeterminism). See RENDERER.md §3.

 Captures tessellated geometry, plain + spanned text runs, and Material Symbols icon
 glyphs. A leaf whose block draws something not-yet-capturable (image blits, saveLayer,
 a rounded/generic layer clip, alpha/blend/colorFilter/renderEffect, span backgrounds)
 — or that hosts a child layer — trips the capture list's `unsupported` flag / defer
 path and falls back to a crisp block-replay (== DeferredRenderNode): always correct,
 just not cached. Image capture is the next step to widen the fast path further.
*/
internal class SdlDisplayListRenderNode : NativeRenderNode {

	override var topLeft: IntOffset = IntOffset.Zero
	override var size: IntSize = IntSize.Zero
	override var pivot: Offset = Offset.Unspecified
	override var alpha: Float = 1f
	override var scaleX: Float = 1f
	override var scaleY: Float = 1f
	override var translationX: Float = 0f
	override var translationY: Float = 0f
	override var rotationX: Float = 0f
	override var rotationY: Float = 0f
	override var rotationZ: Float = 0f
	override var cameraDistance: Float = DefaultCameraDistance
	override var shadowElevation: Float = 0f
	override var ambientShadowColor: Color = Color.Black
	override var spotShadowColor: Color = Color.Black
	override var blendMode: BlendMode = BlendMode.SrcOver
	override var colorFilter: ColorFilter? = null
	override var renderEffect: RenderEffect? = null
	override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
	override var clip: Boolean = false

	private var clipOutline: Outline? = null
	override fun setClipRect(left: Float, top: Float, right: Float, bottom: Float) {
		clipOutline = Outline.Rectangle(Rect(left, top, right, bottom))
	}
	override fun setClipRRect(left: Float, top: Float, right: Float, bottom: Float, radii: FloatArray) {
		clipOutline = Outline.Rounded(
			RoundRect(
				left = left, top = top, right = right, bottom = bottom,
				topLeftCornerRadius = CornerRadius(radii[0], radii[1]),
				topRightCornerRadius = CornerRadius(radii[2], radii[3]),
				bottomRightCornerRadius = CornerRadius(radii[4], radii[5]),
				bottomLeftCornerRadius = CornerRadius(radii[6], radii[7]),
			),
		)
	}
	override fun setClipPath(path: Path?) {
		clipOutline = if (path == null) null else Outline.Generic(path)
	}

	private var recordedBlock: (DrawScope.() -> Unit)? = null
	private var recordedDensity: Density = Density(1f)
	private var recordedLayoutDirection: LayoutDirection = LayoutDirection.Ltr
	private val drawScope = CanvasDrawScope()

	private var displayList: SdlDisplayList? = null
	private var deferMode = false   // sticky: block drew a not-yet-capturable op / has a child layer
	private var sawChild = false    // a child layer drew during this node's record

	// Texture cache for a ROUNDED / GENERIC content-clip leaf. The geo replay clips
	// only to a rect, so a shape-clipped subtree can't ride the geo fast path — it
	// would otherwise block-replay AND re-realize its offscreen rounded mask every
	// frame (the dominant apidemo cost). Instead we bake the clipped content into a
	// layer-local texture ONCE (record()) and blit it under the transform (drawInto),
	// exactly like SdlRenderNode but scoped to the case geo can't cache. A scroll is
	// translation-only, never re-records, so it just blits.
	private var shapeBitmap: ImageBitmap? = null
	private var shapeBitmapW = 0
	private var shapeBitmapH = 0
	private var shapeCached = false

	// Main-loop-thread stack of nodes currently recording. A child's drawInto (invoked
	// while its parent's block runs) flags the parent as a non-leaf so the parent
	// defers — nesting is by DEFER (children composite themselves on the parent's
	// block-replay), avoiding by-value baking + GPU leaks into the capture pass.
	private companion object {
		val fRecordingStack = ArrayList<SdlDisplayListRenderNode>()
	}

	override fun record(
		density: Density,
		layoutDirection: LayoutDirection,
		size: IntSize,
		block: DrawScope.() -> Unit,
	) {
		recordedDensity = density
		recordedLayoutDirection = layoutDirection
		this.size = size
		recordedBlock = block
		displayList = null
		shapeCached = false

		val w = size.width
		val h = size.height
		val renderer = offscreenRenderer as? Sdl3OffscreenRenderer
		if (deferMode || w <= 0 || h <= 0 || renderer == null) return

		// Rounded / generic CONTENT clip → texture-cache instead of block-replaying and
		// re-realizing the mask every frame (see shapeBitmap). Everything else takes the
		// geo-geometry cache below.
		val outline = clipOutline
		if (clip && (outline is Outline.Rounded || outline is Outline.Generic)) {
			recordShapeClipTexture(w, h, block)
			return
		}
		releaseShapeBitmap()

		// Capture at identity base CTM ⇒ layer-local geometry. No GPU touched.
		val list = SdlDisplayList()
		val capture = renderer.createCaptureCanvas(list, Size(w.toFloat(), h.toFloat()))
		sawChild = false
		fRecordingStack.add(this)
		try {
			drawScope.draw(recordedDensity, recordedLayoutDirection, capture, Size(w.toFloat(), h.toFloat()), block)
		} finally {
			fRecordingStack.removeAt(fRecordingStack.size - 1)
			capture.finish()
		}

		if (list.unsupported || sawChild) {
			deferMode = true
		} else {
			displayList = list
		}
	}

	// Bake a rounded/generic-clip leaf's content into an offscreen texture once (the
	// rounded mask realizes here, not every frame). Mirrors SdlRenderNode.record:
	// watches for a child layer (→ defer, children composite live) and for an image
	// blit (partial-alpha content doesn't round-trip bit-exact through the 8-bit
	// premultiplied offscreen → defer). Leaves of shapes + text (the bulk of apidemo's
	// rounded Surfaces) keep the texture and blit it thereafter.
	private fun recordShapeClipTexture(w: Int, h: Int, block: DrawScope.() -> Unit) {
		val renderer = offscreenRenderer ?: run { deferMode = true; return }
		val bmp = ensureShapeBitmap(w, h) ?: run { deferMode = true; return }
		val target = renderer.createCanvas(bmp) ?: run { deferMode = true; return }
		sawChild = false
		val imgBlitsBefore = DrawStats.imageBlits
		fRecordingStack.add(this)
		try {
			// Bake the clip into the texture so the rounded corners are transparent in
			// the cached pixels; content records at layer-local origin (transform is
			// applied at blit). The clip MUST be pushed inside an explicit save frame:
			// a rounded clip realizes as an offscreen mask that only the enclosing
			// frame's restore() composites back — pushed at stack depth 0 it belongs
			// to no frame, nothing composites it, and the bake stays TRANSPARENT
			// (every rounded Surface/chip/icon-button blitted empty).
			target.save()
			applyClip(target)
			drawScope.draw(recordedDensity, recordedLayoutDirection, target, Size(w.toFloat(), h.toFloat()), block)
			target.restore()
		} finally {
			fRecordingStack.removeAt(fRecordingStack.size - 1)
			(target as? NativeFinishableCanvas)?.finish()
		}
		if (sawChild || DrawStats.imageBlits > imgBlitsBefore) {
			deferMode = true
			releaseShapeBitmap()
		} else {
			shapeCached = true
		}
	}

	private fun ensureShapeBitmap(w: Int, h: Int): ImageBitmap? {
		val renderer = offscreenRenderer ?: return null
		if (shapeBitmap == null || shapeBitmapW != w || shapeBitmapH != h) {
			releaseShapeBitmap()
			shapeBitmap = renderer.createImageBitmap(w, h, ImageBitmapConfig.Argb8888, true, ColorSpaces.Srgb)
			shapeBitmapW = w
			shapeBitmapH = h
		}
		return shapeBitmap
	}

	private fun releaseShapeBitmap() {
		(shapeBitmap as? SdlImageBitmap)?.close()
		shapeBitmap = null
		shapeBitmapW = 0
		shapeBitmapH = 0
		shapeCached = false
	}

	override fun drawInto(canvas: Canvas) {
		val block = recordedBlock ?: return
		// Drawn during a PARENT's record → flag the parent as a non-leaf and draw
		// NOTHING now. The parent will defer to a block-replay that draws us normally;
		// this prevents leaking our GPU ops into the target-less capture pass and
		// avoids by-value child baking.
		if (fRecordingStack.isNotEmpty()) {
			fRecordingStack[fRecordingStack.size - 1].sawChild = true
			return
		}
		// Fully transparent layer → skip drawing entirely (upstream skiko skips
		// invisible layers too). Also keeps alpha(0)-hidden hover controls from
		// forcing the enclosing rounded clip to realize its offscreen mask. Must
		// stay AFTER the recording-stack flag: a parent baking around an invisible
		// child must still learn it is a non-leaf, or its cache goes stale when
		// the child fades in.
		if (alpha <= 0.003f) return
		val w = size.width.toFloat()
		val h = size.height.toFloat()

		canvas.save()
		canvas.translate(topLeft.x.toFloat(), topLeft.y.toFloat())

		val hasTransform = scaleX != 1f || scaleY != 1f || rotationZ != 0f ||
			rotationX != 0f || rotationY != 0f || translationX != 0f || translationY != 0f
		if (hasTransform && w > 0f && h > 0f) {
			val pivotX = if (pivot.isUnspecified) w / 2f else pivot.x
			val pivotY = if (pivot.isUnspecified) h / 2f else pivot.y
			val matrix = Matrix()
			prepareLayerTransformationMatrix(
				matrix = matrix,
				pivotX = pivotX, pivotY = pivotY,
				translationX = translationX, translationY = translationY,
				rotationX = rotationX, rotationY = rotationY, rotationZ = rotationZ,
				scaleX = scaleX, scaleY = scaleY, cameraDistance = cameraDistance,
			)
			canvas.concat(matrix)
		}

		// Drop shadow — parent-space, before content (drawn on the real canvas).
		if (shadowElevation > 0f && w > 0f && h > 0f) {
			val outline = clipOutline
			if (outline != null) {
				(canvas as? NativeShadowCanvas)
					?.drawDropShadow(outline, shadowElevation, ambientShadowColor, spotShadowColor)
			}
		}

		// Shape-clip leaf cached as a texture: blit it (the baked-in rounded mask means
		// no per-frame realization). Translation rides the affine, so a scroll just
		// blits. Scale/rotation would bilinear-resample the fixed-resolution texture,
		// and alpha/blend/colorFilter/renderEffect need real offscreen compositing, so
		// those fall through to the exact block-replay below (which re-realizes the
		// mask, as before) — the cache is strictly non-regressing.
		val shapeBmp = shapeBitmap
		if (shapeCached && shapeBmp != null && w > 0f && h > 0f) {
			val hasScaleOrRotation = scaleX != 1f || scaleY != 1f ||
				rotationZ != 0f || rotationX != 0f || rotationY != 0f
			val blitComposites = alpha < 1f || blendMode != BlendMode.SrcOver ||
				colorFilter != null || renderEffect != null
			if (!hasScaleOrRotation && !blitComposites) {
				canvas.drawImageRect(
					image = shapeBmp,
					srcOffset = IntOffset.Zero, srcSize = IntSize(shapeBitmapW, shapeBitmapH),
					dstOffset = IntOffset.Zero, dstSize = IntSize(shapeBitmapW, shapeBitmapH),
					paint = Paint(),
				)
				canvas.restore()
				return
			}
		}

		val list = displayList
		// Layer-level alpha / blend / colorFilter / renderEffect aren't in the captured
		// geometry (they're applied at replay); until geo replay folds them in, defer
		// those to the crisp block-replay.
		//
		// A ROUNDED / PATH content clip also forces the block-replay: the fast path
		// re-emits raw geometry via SDL_RenderGeometry, which the SDL canvas clips only
		// to a RECT (SDL_SetRenderClipRect). Its rounded-corner mask is an offscreen
		// realized lazily from drawRect/admitDraw — a path replayBatch bypasses — so a
		// rounded layer clip would go UNCUT on the fast path. That was the Carousel
		// diff: each item's morphing rounded mask was ignored, so the coloured card
		// overflowed its silhouette (wrong shape + apparent horizontal shift). The
		// block-replay re-runs the block through drawRect, which realizes the mask
		// correctly (== DeferredRenderNode). A plain RECTANGLE clip is honoured by
		// SDL_RenderGeometry directly, so it stays on the fast path.
		val outline = clipOutline
		val hasShapeClip = clip && (outline is Outline.Rounded || outline is Outline.Generic)
		val needsCompositing = alpha < 1f || blendMode != BlendMode.SrcOver ||
			colorFilter != null || renderEffect != null || hasShapeClip

		if (list != null && !needsCompositing && canvas is Sdl3Canvas && w > 0f && h > 0f) {
			// GEO fast path: re-emit captured commands (geometry + text) through the
			// current transform. Crisp at any scale/rotation, bit-exact, no texture.
			// A rectangular content clip narrows the SDL clip rect first (honoured by
			// SDL_RenderGeometry), keeping cached geometry inside the layer bounds.
			if (clip && outline is Outline.Rectangle) {
				canvas.clipRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom)
			}
			canvas.replayDisplayList(list)
		} else {
			// Block-replay fallback (== DeferredRenderNode): clip + alpha + re-run block.
			if (clip && w > 0f && h > 0f) applyClip(canvas)
			val needsAlphaLayer = alpha < 1f && w > 0f && h > 0f
			if (needsAlphaLayer) {
				canvas.saveLayer(Rect(0f, 0f, w, h), Paint().apply { alpha = this@SdlDisplayListRenderNode.alpha })
			}
			drawScope.draw(recordedDensity, recordedLayoutDirection, canvas, Size(w, h), block)
			if (needsAlphaLayer) canvas.restore()
		}

		canvas.restore()
	}

	private fun applyClip(canvas: Canvas) {
		when (val outline = clipOutline) {
			is Outline.Rectangle -> canvas.clipRect(
				outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom,
			)
			is Outline.Rounded -> {
				val shapeClip = canvas as? NativeShapeClipCanvas
				if (shapeClip != null) shapeClip.clipRoundRect(outline.roundRect)
				else { val p = Path().apply { addRoundRect(outline.roundRect) }; canvas.clipPath(p) }
			}
			is Outline.Generic -> canvas.clipPath(outline.path)
			null -> {}
		}
	}

	override fun close() {
		recordedBlock = null
		displayList = null
		releaseShapeBitmap()
	}
}
