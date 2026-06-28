package androidx.compose.ui.graphics

import androidx.compose.ui.Modifier
import com.compose.desktop.native.element.GraphicsLayerModifier

// ==================
// MARK: TransformOrigin
// ==================

/* Pivot point for scale / rotation, expressed as fractions of the node's
   bounds (0,0 = top-left, 1,1 = bottom-right). Default (0.5, 0.5) is the
   centre of the node. */
data class TransformOrigin(val pivotFractionX: Float, val pivotFractionY: Float) {
	companion object {
		val Center = TransformOrigin(0.5f, 0.5f)
	}
}

// GraphicsLayerModifier (the element this builds) lives in
// com.compose.desktop.native.element.

// ==================
// MARK: GraphicsLayerScope
// ==================

/* Surface-match reshape (FIDELITY: render-bridge middleware). Upstream's
   GraphicsLayerScope is a 500-line interface extending Density and exposing
   a backing GraphicsLayer (expect class). We expose just the public
   property surface that upstream call sites read/write inside the
   `Modifier.graphicsLayer { ... }` lambda — alpha, scale, translation,
   rotation, transformOrigin, shape, clip, shadowElevation, cameraDistance,
   compositingStrategy. The lambda body builds a snapshot which the modifier
   then folds into our existing GraphicsLayerModifier element.

   This lets us vendor downstream files (Crossfade, EnterExitTransition,
   etc.) that use the lambda overload without having to also port upstream's
   GraphicsLayer expect class. */
interface GraphicsLayerScope {
	var scaleX: Float
	var scaleY: Float
	var alpha: Float
	var translationX: Float
	var translationY: Float
	var shadowElevation: Float
	var ambientShadowColor: Color
	var spotShadowColor: Color
	var rotationX: Float
	var rotationY: Float
	var rotationZ: Float
	var cameraDistance: Float
	var transformOrigin: TransformOrigin
	var shape: Shape
	var clip: Boolean
	var compositingStrategy: Int  // upstream is a value class; reduced to Int (0 = auto, 1 = offscreen, 2 = modulateAlpha)
	val size: androidx.compose.ui.geometry.Size
		get() = androidx.compose.ui.geometry.Size.Zero
}

/* Builder receiver passed to the `Modifier.graphicsLayer { ... }` lambda. */
internal class GraphicsLayerScopeImpl : GraphicsLayerScope {
	override var scaleX: Float = 1f
	override var scaleY: Float = 1f
	override var alpha: Float = 1f
	override var translationX: Float = 0f
	override var translationY: Float = 0f
	override var shadowElevation: Float = 0f
	override var ambientShadowColor: Color = Color.Black
	override var spotShadowColor: Color = Color.Black
	override var rotationX: Float = 0f
	override var rotationY: Float = 0f
	override var rotationZ: Float = 0f
	override var cameraDistance: Float = 8f
	override var transformOrigin: TransformOrigin = TransformOrigin.Center
	override var shape: Shape = RectangleShape
	override var clip: Boolean = false
	override var compositingStrategy: Int = 0
}

// ==================
// MARK: Modifier.graphicsLayer
// ==================

/* Apply a graphics layer to this node and its subtree. See
   GraphicsLayerModifier for caching semantics. Returns `this` unchanged
   when the requested layer is a no-op. */
fun Modifier.graphicsLayer(
	alpha: Float = 1f,
	scaleX: Float = 1f,
	scaleY: Float = 1f,
	rotationZ: Float = 0f,
	translationX: Float = 0f,
	translationY: Float = 0f,
	transformOrigin: TransformOrigin = TransformOrigin.Center,
	cacheKey: Any? = null,
	@Suppress("UNUSED_PARAMETER") clip: Boolean = false,
): Modifier {
	val vMod = GraphicsLayerModifier(
		alpha = alpha.coerceIn(0f, 1f),
		scaleX = scaleX,
		scaleY = scaleY,
		rotationZ = rotationZ,
		translationX = translationX,
		translationY = translationY,
		transformOrigin = transformOrigin,
		cacheKey = cacheKey,
	)
	return if (vMod.isIdentity) this else then(vMod)
}

/* Block-form Modifier.graphicsLayer. Builds a GraphicsLayerScope, runs the
   block to populate it, then folds the snapshot into a GraphicsLayerModifier.
   Renderer still reads alpha / scale / translation / rotation / origin from
   the resulting element; the other GraphicsLayerScope properties (shape,
   clip, shadow, etc.) are accept-and-ignore today. */
fun Modifier.graphicsLayer(block: GraphicsLayerScope.() -> Unit): Modifier {
	val vScope = GraphicsLayerScopeImpl().apply(block)
	val vMod = GraphicsLayerModifier(
		alpha = vScope.alpha.coerceIn(0f, 1f),
		scaleX = vScope.scaleX,
		scaleY = vScope.scaleY,
		rotationZ = vScope.rotationZ,
		translationX = vScope.translationX,
		translationY = vScope.translationY,
		transformOrigin = vScope.transformOrigin,
		cacheKey = null,
	)
	return if (vMod.isIdentity) this else then(vMod)
}
