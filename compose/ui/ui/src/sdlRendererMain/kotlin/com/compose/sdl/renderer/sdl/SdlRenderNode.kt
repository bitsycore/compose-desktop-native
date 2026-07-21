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
import com.compose.sdl.graphics.NativeRenderNode
import com.compose.sdl.graphics.NativeShadowCanvas
import com.compose.sdl.graphics.NativeShapeClipCanvas
import com.compose.sdl.graphics.offscreenRenderer
import com.compose.sdl.graphics.prepareLayerTransformationMatrix

// ==================
// MARK: SdlRenderNode — RETAINED (texture-cached) NativeRenderNode
// ==================

/**
 The texture-caching node (CDN_LAYERCACHE=texture — a fallback; the geo
 display-list node SdlDisplayListRenderNode is the default, see RENDERER.md §3).
 Records a layer's drawing ONCE into an offscreen SDL texture; replay is a cheap
 textured blit under the layer transform, NOT a re-tessellation (compare
 DeferredRenderNode, which re-runs the block every replay).

 CORRECT-BY-CONSTRUCTION nesting, no upstream divergence: SDL textures composite
 children BY VALUE, unlike skiko's by-reference Picture, so a naive "cache every
 layer" would show stale children. Instead we **cache leaves, defer parents**: a
 node auto-detects on its first record whether its block drew any CHILD layer (via
 the recording stack below). If it did → it's a parent → fall back to replay-the-
 block (children composite their own live textures, so no staleness). If it didn't
 → it's a leaf → keep the texture and just blit it henceforth. Leaves (text runs,
 icons, shapes — the bulk of static content like the sidebar) stop re-tessellating;
 parents cheaply re-composite.
*/
internal class SdlRenderNode : NativeRenderNode {

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

	// ============
	//  Record

	private var recordedBlock: (DrawScope.() -> Unit)? = null
	private var recordedDensity: Density = Density(1f)
	private var recordedLayoutDirection: LayoutDirection = LayoutDirection.Ltr
	private val drawScope = CanvasDrawScope()

	// Caching state (auto-detected — see class doc).
	private var deferMode = false          // true once a child layer was seen: replay the block
	private var cached = false             // true once a leaf texture is valid
	private var bitmap: ImageBitmap? = null
	private var bitmapW = 0
	private var bitmapH = 0
	private var sawChildDuringRecord = false

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
		cached = false

		val w = size.width
		val h = size.height
		val renderer = offscreenRenderer
		// Parents (deferMode) / zero-size / no offscreen support → just keep the block
		// and replay it in drawInto (identical to DeferredRenderNode).
		if (deferMode || w <= 0 || h <= 0 || renderer == null) return

		// Attempt a leaf-cache: render the block into an offscreen texture, watching
		// for any child layer being drawn (which flips us to deferMode).
		val bmp = ensureBitmap(w, h) ?: return
		val target = renderer.createCanvas(bmp) ?: return

		sawChildDuringRecord = false
		// Watch for image/vector/icon blits: content with partial alpha (a decoded
		// image, a tinted icon) does NOT round-trip bit-exact through the 8-bit
		// premultiplied offscreen (the heart painterResource rendered visibly wrong),
		// so a leaf that draws any image bails to the crisp block-replay. Text/shapes/
		// solid fills bump other DrawStats counters and stay cached (pixel-equal).
		// (A true bit-exact cache of such content is SdlDisplayListRenderNode's
		// geometry list, which has no texture round-trip.) Costs little: icons are
		// blits, not tessellation — the big tessellation cost is text, which still caches.
		val imgBlitsBefore = DrawStats.imageBlits
		fRecordingStack.add(this)
		try {
			// Content is recorded at layer-local origin; the transform is applied at
			// replay (blit). Clip is baked into the texture so rounded corners are
			// transparent in the cached pixels. Push the clip inside an explicit save
			// frame: a rounded clip realizes as an offscreen mask that only the
			// enclosing frame's restore() composites — pushed at depth 0 it is never
			// composited and the bake stays transparent (see SdlDisplayListRenderNode).
			target.save()
			if (clip) applyClip(target)
			drawScope.draw(recordedDensity, recordedLayoutDirection, target, Size(w.toFloat(), h.toFloat()), block)
			target.restore()
		} finally {
			fRecordingStack.removeAt(fRecordingStack.size - 1)
			(target as? com.compose.sdl.graphics.NativeFinishableCanvas)?.finish()
		}
		val drewImage = DrawStats.imageBlits > imgBlitsBefore

		if (sawChildDuringRecord || drewImage) {
			// Not a cacheable leaf (has a child layer, or drew an image) — abandon
			// caching for this node's lifetime and replay the block instead.
			deferMode = true
			releaseBitmap()
		} else {
			cached = true
		}
	}

	private fun ensureBitmap(w: Int, h: Int): ImageBitmap? {
		val renderer = offscreenRenderer ?: return null
		if (bitmap == null || bitmapW != w || bitmapH != h) {
			releaseBitmap()
			bitmap = renderer.createImageBitmap(w, h, ImageBitmapConfig.Argb8888, true, ColorSpaces.Srgb)
			bitmapW = w
			bitmapH = h
		}
		return bitmap
	}

	private fun releaseBitmap() {
		(bitmap as? SdlImageBitmap)?.close()
		bitmap = null
		bitmapW = 0
		bitmapH = 0
	}

	// ============
	//  Replay

	override fun drawInto(canvas: Canvas) {
		val block = recordedBlock ?: return
		val w = size.width.toFloat()
		val h = size.height.toFloat()

		// If I am being drawn while a parent is recording, that parent has a child
		// layer (me) → it must NOT leaf-cache. (Detected here rather than in the
		// parent so it works for any nesting depth.)
		if (fRecordingStack.isNotEmpty()) fRecordingStack[fRecordingStack.size - 1].sawChildDuringRecord = true

		// Fully transparent layer → skip drawing entirely (see SdlDisplayListRenderNode).
		if (alpha <= 0.003f) return

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

		// Drop shadow — parent-space, before content (never baked into the texture).
		if (shadowElevation > 0f && w > 0f && h > 0f) {
			val outline = clipOutline
			if (outline != null) {
				(canvas as? NativeShadowCanvas)
					?.drawDropShadow(outline, shadowElevation, ambientShadowColor, spotShadowColor)
			}
		}

		// Blit the cached texture ONLY for an opaque, untransformed, effect-free leaf
		// — the exact subset that a plain blit reproduces pixel-equal (verified). We
		// bail to a crisp block-replay when:
		//   * scale/rotation — a fixed-resolution texture would bilinear-resample (soft);
		//   * alpha<1 / blendMode / colorFilter / renderEffect — the blit can't
		//     reproduce the offscreen compositing (premultiplied-alpha blit differs).
		// So caching is strictly non-regressing: the static-content case (the win)
		// blits; everything else replays exactly as before. Phase 4's cached-geometry
		// display list would widen this (crisp under transform, real compositing).
		val hasScaleOrRotation = scaleX != 1f || scaleY != 1f ||
			rotationZ != 0f || rotationX != 0f || rotationY != 0f
		val needsCompositing = alpha < 1f || blendMode != BlendMode.SrcOver ||
			colorFilter != null || renderEffect != null
		val bmp = bitmap
		if (cached && bmp != null && !hasScaleOrRotation && !needsCompositing && w > 0f && h > 0f) {
			// Leaf fast path: blit the cached texture (clip already baked in).
			canvas.drawImageRect(
				image = bmp,
				srcOffset = IntOffset.Zero,
				srcSize = IntSize(bitmapW, bitmapH),
				dstOffset = IntOffset.Zero,
				dstSize = IntSize(bitmapW, bitmapH),
				paint = Paint(),
			)
		} else {
			// Parent / uncached fallback: replay the block (== DeferredRenderNode).
			if (clip && w > 0f && h > 0f) applyClip(canvas)
			val needsAlphaLayer = alpha < 1f && w > 0f && h > 0f
			if (needsAlphaLayer) {
				canvas.saveLayer(Rect(0f, 0f, w, h), Paint().apply { alpha = this@SdlRenderNode.alpha })
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
		releaseBitmap()
	}

	// The main-loop-thread stack of nodes currently recording. A child's drawInto
	// (invoked while its parent's block runs) flags the parent as a non-leaf.
	private companion object {
		val fRecordingStack = ArrayList<SdlRenderNode>()
	}
}
