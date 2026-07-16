package com.compose.sdl.graphics

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

// ==================
// MARK: DeferredRenderNode — replay-the-block NativeRenderNode
// ==================

/*
 The renderer-agnostic [NativeRenderNode] both renderers use today. It does NOT
 cache rasterised output: [record] stores the draw block; [drawInto] REPLAYS it
 against the target canvas under the node's transform / clip / shadow / alpha. This
 is behaviour-identical to the pre-refactor paths it replaces — the old
 GraphicsLayer.native lambda-replay AND ProjectOwnedLayer's transform/shadow/clip —
 now unified in one place behind the RenderNode seam.

 It is the correct-but-unoptimised node. The perf win (stop re-tessellating on
 replay) comes from swapping the per-renderer createNativeRenderNode actual to a
 caching node — skiko RenderNode (a real display list) on Skia, an offscreen /
 cached-geometry display list on SDL — WITHOUT touching GraphicsLayer. See
 RENDERER_REFACTOR.md §4 (Phase 2 = that swap).

 Transform is applied via [prepareLayerTransformationMatrix] + canvas.concat, the
 SAME matrix GraphicsLayerOwnerLayer uses for hit-testing (mapOffset), so draw and
 hit-test provably agree. Shadow + rounded/shape clip go through the project canvas
 interfaces (NativeShadowCanvas / NativeShapeClipCanvas), exactly as the old
 ProjectOwnedLayer did.
*/
internal class DeferredRenderNode : NativeRenderNode {

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

	// Outline pushed by GraphicsLayer.configureOutlineAndClip whenever clip OR a
	// shadow is present — so it drives BOTH the content clip (when clip=true) and
	// the drop-shadow silhouette (when shadowElevation>0, even with clip=false).
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
	//  Record — store the block (deferred; replayed in drawInto).

	private var recordedBlock: (DrawScope.() -> Unit)? = null
	private var recordedDensity: Density = Density(1f)
	private var recordedLayoutDirection: LayoutDirection = LayoutDirection.Ltr
	private val drawScope = CanvasDrawScope()

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
	}

	override fun drawInto(canvas: Canvas) {
		val block = recordedBlock ?: return
		val w = size.width.toFloat()
		val h = size.height.toFloat()

		canvas.save()
		canvas.translate(topLeft.x.toFloat(), topLeft.y.toFloat())

		// Transform about the pivot (default centre) — via the SAME matrix used for
		// hit-testing, so draw and hit-test agree.
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

		// Drop shadow — layer-local, BEFORE the clip (shadow lives outside bounds).
		if (shadowElevation > 0f && w > 0f && h > 0f) {
			val outline = clipOutline
			if (outline != null) {
				(canvas as? NativeShadowCanvas)
					?.drawDropShadow(outline, shadowElevation, ambientShadowColor, spotShadowColor)
			}
		}

		// Content clip.
		if (clip && w > 0f && h > 0f) applyClip(canvas)

		// Offscreen for alpha (behaviour-preserving: the old paths saveLayer'd on
		// alpha<1). ModulateAlpha suppresses the offscreen (alpha folds per-op) — but
		// our DrawScope has no per-op multiplier yet, so it still composites here.
		val needsAlphaLayer = alpha < 1f && w > 0f && h > 0f
		if (needsAlphaLayer) {
			canvas.saveLayer(
				Rect(0f, 0f, w, h),
				Paint().apply { alpha = this@DeferredRenderNode.alpha },
			)
		}

		drawScope.draw(recordedDensity, recordedLayoutDirection, canvas, Size(w, h), block)

		if (needsAlphaLayer) canvas.restore()
		canvas.restore()
	}

	private fun applyClip(canvas: Canvas) {
		when (val outline = clipOutline) {
			is Outline.Rectangle -> canvas.clipRect(
				outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom,
			)
			is Outline.Rounded -> {
				// Skia clips rounded natively via clipPath; the SDL canvas has no
				// path-clip primitive, so it implements NativeShapeClipCanvas and clips
				// the rounded outline via an offscreen mask (preferred over clipPath,
				// which falls back to the bounding rect there and left rounded state
				// layers looking square).
				val shapeClip = canvas as? NativeShapeClipCanvas
				if (shapeClip != null) {
					shapeClip.clipRoundRect(outline.roundRect)
				} else {
					val path = Path().apply { addRoundRect(outline.roundRect) }
					canvas.clipPath(path)
				}
			}
			is Outline.Generic -> canvas.clipPath(outline.path)
			null -> {}
		}
	}

	override fun close() {
		recordedBlock = null
	}
}
