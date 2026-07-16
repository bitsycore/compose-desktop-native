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
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.DefaultCameraDistance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.compose.sdl.graphics.NativeRenderNode
import com.compose.sdl.graphics.NativeShadowCanvas
import com.compose.sdl.graphics.NativeShapeClipCanvas
import com.compose.sdl.graphics.offscreenRenderer
import com.compose.sdl.graphics.prepareLayerTransformationMatrix

// ==================
// MARK: SdlDisplayListRenderNode — cached-GEOMETRY retained node (Phase 4)
// ==================

/*
 The robust retained node. record() captures a leaf's tessellated geometry ONCE (in
 layer-local space, via a capture canvas that touches no GPU); drawInto() re-emits
 those cached vertices through the layer transform — crisp under ANY transform
 (vertices re-transformed, not a resampled texture), bit-exact (no texture round-trip),
 and with NO per-frame render-target state (so none of the texture node's timing-
 dependent nondeterminism). See RENDERER_REFACTOR.md §3/§13.

 First increment captures GEOMETRY only. A leaf whose block draws text / images / clip
 / saveLayer trips the capture list's `unsupported` flag and falls back to a crisp
 block-replay (== DeferredRenderNode) — so it's always correct, just not yet cached.
 Extending capture to those op types is the next step (widens the fast path + the win).
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
	private var deferMode = false   // sticky: block drew a not-yet-capturable op

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

		val w = size.width
		val h = size.height
		val renderer = offscreenRenderer as? Sdl3OffscreenRenderer
		if (deferMode || w <= 0 || h <= 0 || renderer == null) return

		// Capture at identity base CTM ⇒ layer-local geometry. No GPU touched.
		val list = SdlDisplayList()
		val capture = renderer.createCaptureCanvas(list, Size(w.toFloat(), h.toFloat()))
		drawScope.draw(recordedDensity, recordedLayoutDirection, capture, Size(w.toFloat(), h.toFloat()), block)
		capture.finish()

		if (list.unsupported) {
			deferMode = true
		} else {
			displayList = list
		}
	}

	override fun drawInto(canvas: Canvas) {
		val block = recordedBlock ?: return
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

		val list = displayList
		// Layer-level alpha / blend / colorFilter / renderEffect aren't in the captured
		// geometry (they're applied at replay); until geo replay folds them in, defer
		// those to the crisp block-replay.
		val needsCompositing = alpha < 1f || blendMode != BlendMode.SrcOver ||
			colorFilter != null || renderEffect != null

		if (list != null && !needsCompositing && canvas is Sdl3Canvas && w > 0f && h > 0f) {
			// GEO fast path: re-emit captured layer-local vertices through the current
			// transform. Crisp at any scale/rotation, bit-exact, no texture.
			canvas.replayGeometryList(list)
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
	}
}
