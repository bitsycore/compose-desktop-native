package androidx.compose.ui.graphics.layer

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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.compose.sdl.graphics.NativeRenderNode

// ==================
// MARK: GraphicsLayer — façade over NativeRenderNode
// ==================
//
// DERIVED (copy + edit) from compose-multiplatform-core
//   ui-graphics/src/skikoMain/.../graphics/layer/SkiaGraphicsLayer.skiko.kt.
// VENDOR-BASE: compose/ui/ui-graphics/src/skikoMain/kotlin/androidx/compose/ui/graphics/layer/SkiaGraphicsLayer.skiko.kt @ v1.12.0-beta03+dev4483
// The ONLY structural change vs upstream: the backing display-list node is our
// renderer-agnostic `NativeRenderNode` instead of `org.jetbrains.skiko.node.RenderNode`,
// so both renderers share this façade (Skia's node wraps the real skiko RenderNode;
// SDL's node is SdlRenderNode). Every visual property is mirrored onto the node and
// applied at replay, exactly like upstream. Trimmed vs upstream: outsets (blur
// expansion) and ChildLayerDependenciesTracker (prompt child release — our children
// release via NativeReleaseQueue/GC instead). See RENDERER_CONVERGE.md §4 (B2).

@Suppress("PropertyName")
actual class GraphicsLayer internal constructor(
	private val renderNode: NativeRenderNode,
) {

	private var outlineDirty = true
	private var roundRectOutlineTopLeft: Offset = Offset.Zero
	private var roundRectOutlineSize: Size = Size.Unspecified
	private var roundRectCornerRadius: Float = 0f
	private var internalOutline: Outline? = null
	private var outlinePath: Path? = null

	actual var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
		set(value) {
			if (field != value) {
				field = value
				renderNode.compositingStrategy = value
			}
		}

	actual var topLeft: IntOffset = IntOffset.Zero
		set(value) {
			if (field != value) {
				field = value
				renderNode.topLeft = value
			}
		}

	actual var size: IntSize = IntSize.Zero
		private set(value) {
			if (field != value) {
				field = value
				renderNode.size = value
				if (roundRectOutlineSize.isUnspecified) {
					outlineDirty = true
					configureOutlineAndClip()
				}
			}
		}

	actual var pivotOffset: Offset = Offset.Unspecified
		set(value) {
			if (field != value) {
				field = value
				renderNode.pivot = value
			}
		}

	actual var alpha: Float = 1f
		set(value) {
			if (field != value) {
				field = value
				renderNode.alpha = value
			}
		}

	actual var scaleX: Float = 1f
		set(value) {
			if (field != value) { field = value; renderNode.scaleX = value }
		}

	actual var scaleY: Float = 1f
		set(value) {
			if (field != value) { field = value; renderNode.scaleY = value }
		}

	actual var translationX: Float = 0f
		set(value) {
			if (field != value) { field = value; renderNode.translationX = value }
		}

	actual var translationY: Float = 0f
		set(value) {
			if (field != value) { field = value; renderNode.translationY = value }
		}

	actual var shadowElevation: Float = 0f
		set(value) {
			if (field != value) {
				field = value
				renderNode.shadowElevation = value
				outlineDirty = true
				configureOutlineAndClip()
			}
		}

	actual var ambientShadowColor: Color = Color.Black
		set(value) {
			if (field != value) { field = value; renderNode.ambientShadowColor = value }
		}

	actual var spotShadowColor: Color = Color.Black
		set(value) {
			if (field != value) { field = value; renderNode.spotShadowColor = value }
		}

	actual var blendMode: BlendMode = BlendMode.SrcOver
		set(value) {
			if (field != value) { field = value; renderNode.blendMode = value }
		}

	actual var colorFilter: ColorFilter? = null
		set(value) {
			if (field != value) { field = value; renderNode.colorFilter = value }
		}

	actual val outline: Outline
		get() {
			val tmpOutline = internalOutline
			val tmpPath = outlinePath
			return if (tmpOutline != null) {
				tmpOutline
			} else if (tmpPath != null) {
				Outline.Generic(tmpPath).also { internalOutline = it }
			} else {
				resolveOutlinePosition { outlineTopLeft, outlineSize ->
					val left = outlineTopLeft.x
					val top = outlineTopLeft.y
					val right = left + outlineSize.width
					val bottom = top + outlineSize.height
					val cornerRadius = this.roundRectCornerRadius
					if (cornerRadius > 0f) {
						Outline.Rounded(RoundRect(left, top, right, bottom, CornerRadius(cornerRadius)))
					} else {
						Outline.Rectangle(Rect(left, top, right, bottom))
					}
				}.also { internalOutline = it }
			}
		}

	private fun resetOutlineParams() {
		internalOutline = null
		outlinePath = null
		roundRectOutlineSize = Size.Unspecified
		roundRectOutlineTopLeft = Offset.Zero
		roundRectCornerRadius = 0f
		outlineDirty = true
	}

	actual fun setPathOutline(path: Path) {
		resetOutlineParams()
		this.outlinePath = path
		configureOutlineAndClip()
	}

	actual fun setRoundRectOutline(topLeft: Offset, size: Size, cornerRadius: Float) {
		if (this.roundRectOutlineTopLeft != topLeft ||
			this.roundRectOutlineSize != size ||
			this.roundRectCornerRadius != cornerRadius ||
			this.outlinePath != null
		) {
			resetOutlineParams()
			this.roundRectOutlineTopLeft = topLeft
			this.roundRectOutlineSize = size
			this.roundRectCornerRadius = cornerRadius
			configureOutlineAndClip()
		}
	}

	actual fun setRectOutline(topLeft: Offset, size: Size) {
		setRoundRectOutline(topLeft, size, 0f)
	}

	actual var rotationX: Float = 0f
		set(value) {
			if (field != value) { field = value; renderNode.rotationX = value }
		}

	actual var rotationY: Float = 0f
		set(value) {
			if (field != value) { field = value; renderNode.rotationY = value }
		}

	actual var rotationZ: Float = 0f
		set(value) {
			if (field != value) { field = value; renderNode.rotationZ = value }
		}

	actual var cameraDistance: Float = DefaultCameraDistance
		set(value) {
			if (field != value) { field = value; renderNode.cameraDistance = value }
		}

	actual var clip: Boolean = false
		set(value) {
			if (field != value) {
				field = value
				renderNode.clip = value
				outlineDirty = true
				configureOutlineAndClip()
			}
		}

	actual var renderEffect: RenderEffect? = null
		set(value) {
			if (field != value) { field = value; renderNode.renderEffect = value }
		}

	actual var isReleased: Boolean = false
		internal set

	actual fun setOutsets(left: Int, top: Int, right: Int, bottom: Int) {
		// Outsets (blur/shadow expansion) not modelled on this port — see header.
	}

	actual fun record(
		density: Density,
		layoutDirection: LayoutDirection,
		size: IntSize,
		block: DrawScope.() -> Unit,
	) {
		this.size = size
		renderNode.record(density, layoutDirection, size, block)
	}

	internal actual fun draw(canvas: Canvas, parentLayer: GraphicsLayer?) {
		if (isReleased) return
		configureOutlineAndClip()
		renderNode.drawInto(canvas)
	}

	@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
	private fun configureOutlineAndClip() {
		if (!outlineDirty) return
		val outlineIsNeeded = clip || shadowElevation > 0f
		if (!outlineIsNeeded) {
			renderNode.clip = false
			renderNode.setClipPath(null)
		} else {
			renderNode.clip = clip
			when (val tmpOutline = outline) {
				is Outline.Rectangle -> renderNode.setClipRect(
					tmpOutline.rect.left, tmpOutline.rect.top,
					tmpOutline.rect.right, tmpOutline.rect.bottom,
				)
				is Outline.Rounded -> renderNode.setClipRRect(
					tmpOutline.roundRect.left, tmpOutline.roundRect.top,
					tmpOutline.roundRect.right, tmpOutline.roundRect.bottom,
					floatArrayOf(
						tmpOutline.roundRect.topLeftCornerRadius.x,
						tmpOutline.roundRect.topLeftCornerRadius.y,
						tmpOutline.roundRect.topRightCornerRadius.x,
						tmpOutline.roundRect.topRightCornerRadius.y,
						tmpOutline.roundRect.bottomRightCornerRadius.x,
						tmpOutline.roundRect.bottomRightCornerRadius.y,
						tmpOutline.roundRect.bottomLeftCornerRadius.x,
						tmpOutline.roundRect.bottomLeftCornerRadius.y,
					),
				)
				is Outline.Generic -> renderNode.setClipPath(tmpOutline.path)
			}
		}
		outlineDirty = false
	}

	private inline fun <T> resolveOutlinePosition(block: (Offset, Size) -> T): T {
		val layerSize = this.size.toSize()
		val rRectTopLeft = roundRectOutlineTopLeft
		val rRectSize = roundRectOutlineSize
		val outlineSize = if (rRectSize.isUnspecified) layerSize else rRectSize
		return block(rRectTopLeft, outlineSize)
	}

	internal fun release() {
		if (!isReleased) {
			isReleased = true
			renderNode.close()
		}
	}

	// Mirrors upstream SkiaGraphicsLayer.toImageBitmap: render the layer into a fresh
	// bitmap of its own size. The offscreen canvas is flushed via NativeFinishableCanvas
	// (SDL batches geometry; the backing render-target texture must be committed before
	// it's read) — Skia's finish() is a no-op.
	actual suspend fun toImageBitmap(): ImageBitmap {
		val bitmap = ImageBitmap(size.width, size.height)
		val canvas = Canvas(bitmap)
		draw(canvas, null)
		(canvas as? com.compose.sdl.graphics.NativeFinishableCanvas)?.finish()
		return bitmap
	}
}
