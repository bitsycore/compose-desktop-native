package androidx.compose.ui.graphics.layer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

// ==================
// MARK: GraphicsLayer — native no-op actual
// ==================
//
// Upstream's skiko actual (SkiaGraphicsLayer.skiko.kt, 513L) uses Skia's
// Picture/Canvas API for record + replay. We don't have skiko on the
// SDL3 path and the Skia path doesn't route through GraphicsLayer yet
// — the renderer's per-node draw does its own composition. So this
// actual is a plain field bag with no rendering behavior: setters store
// their values, getters return them, record/draw/toImageBitmap are
// no-ops. Nothing in our pipeline constructs a GraphicsLayer yet.

@Suppress("PropertyName", "unused")
actual class GraphicsLayer {

	actual var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
	actual var topLeft: IntOffset = IntOffset.Zero
	actual var size: IntSize = IntSize.Zero
	actual var pivotOffset: Offset = Offset.Unspecified
	actual var alpha: Float = 1f
	actual var scaleX: Float = 1f
	actual var scaleY: Float = 1f
	actual var translationX: Float = 0f
	actual var translationY: Float = 0f
	actual var shadowElevation: Float = 0f
	actual var ambientShadowColor: Color = Color.Black
	actual var spotShadowColor: Color = Color.Black
	actual var blendMode: BlendMode = BlendMode.SrcOver
	actual var colorFilter: ColorFilter? = null

	private var outlineField: Outline = Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
	actual val outline: Outline get() = outlineField

	actual fun setOutsets(left: Int, top: Int, right: Int, bottom: Int) { /* no-op */ }
	actual fun setPathOutline(path: Path) { outlineField = Outline.Generic(path) }
	actual fun setRoundRectOutline(topLeft: Offset, size: Size, cornerRadius: Float) {
		outlineField = Outline.Rounded(
			androidx.compose.ui.geometry.RoundRect(
				left = topLeft.x, top = topLeft.y,
				right = topLeft.x + size.width, bottom = topLeft.y + size.height,
				cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
			)
		)
	}
	actual fun setRectOutline(topLeft: Offset, size: Size) {
		val effTopLeft = if (topLeft == Offset.Unspecified) Offset.Zero else topLeft
		val effSize = if (size == Size.Unspecified) this.size.let { Size(it.width.toFloat(), it.height.toFloat()) } else size
		outlineField = Outline.Rectangle(
			androidx.compose.ui.geometry.Rect(
				effTopLeft.x, effTopLeft.y,
				effTopLeft.x + effSize.width, effTopLeft.y + effSize.height,
			)
		)
	}

	actual var rotationX: Float = 0f
	actual var rotationY: Float = 0f
	actual var rotationZ: Float = 0f
	actual var cameraDistance: Float = DefaultCameraDistance
	actual var clip: Boolean = false
	actual var renderEffect: RenderEffect? = null
	actual var isReleased: Boolean = false
		internal set

	// ============
	//  Record / draw — lambda replay
	//
	// Upstream's skiko actual records into a Skia Picture. This renderer is
	// immediate-mode, so record() stores the draw lambda and draw() REPLAYS it
	// with the layer's transform / alpha / clip applied. Same visual result as
	// a display list as long as callers re-record on invalidation (they do —
	// that's the GraphicsLayer contract). This is what makes GraphicsContext
	// consumers render: SharedTransitionLayout's overlay, rememberGraphicsLayer.

	private var recordedBlock: (DrawScope.() -> Unit)? = null
	private var recordedDensity: Density = Density(1f)
	private var recordedLayoutDirection: LayoutDirection = LayoutDirection.Ltr
	private val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()

	actual fun record(
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

	actual suspend fun toImageBitmap(): ImageBitmap =
		throw NotImplementedError("GraphicsLayer.toImageBitmap not implemented on desktop")

	internal actual fun draw(canvas: Canvas, parentLayer: GraphicsLayer?) {
		if (isReleased) return
		val vBlock = recordedBlock ?: return
		canvas.save()
		canvas.translate(topLeft.x.toFloat(), topLeft.y.toFloat())
		// Transform about the pivot (defaults to the layer centre).
		if (scaleX != 1f || scaleY != 1f || rotationZ != 0f || translationX != 0f || translationY != 0f) {
			val vPivot =
				if (pivotOffset == Offset.Unspecified) Offset(size.width / 2f, size.height / 2f)
				else pivotOffset
			canvas.translate(translationX + vPivot.x, translationY + vPivot.y)
			if (scaleX != 1f || scaleY != 1f) canvas.scale(scaleX, scaleY)
			if (rotationZ != 0f) canvas.rotate(rotationZ)
			canvas.translate(-vPivot.x, -vPivot.y)
		}
		if (clip) {
			// Rect/rounded outlines clip to their bounds (SDL clips are rects;
			// rounded-corner exactness would need the offscreen mask path);
			// generic outlines go through clipPath (bbox fallback on SDL).
			when (val vOutline = outline) {
				is Outline.Rectangle -> canvas.clipRect(vOutline.rect)
				is Outline.Rounded -> canvas.clipRect(
					androidx.compose.ui.geometry.Rect(
						vOutline.roundRect.left, vOutline.roundRect.top,
						vOutline.roundRect.right, vOutline.roundRect.bottom,
					),
				)
				is Outline.Generic -> canvas.clipPath(vOutline.path)
			}
		}
		val vNeedsAlphaLayer = alpha < 1f
		if (vNeedsAlphaLayer) {
			canvas.saveLayer(
				androidx.compose.ui.geometry.Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()),
				androidx.compose.ui.graphics.Paint().apply { this.alpha = this@GraphicsLayer.alpha },
			)
		}
		drawScope.draw(
			recordedDensity,
			recordedLayoutDirection,
			canvas,
			Size(size.width.toFloat(), size.height.toFloat()),
			vBlock,
		)
		if (vNeedsAlphaLayer) canvas.restore()
		canvas.restore()
	}
}
