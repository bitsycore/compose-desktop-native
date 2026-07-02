package androidx.compose.ui.graphics

import androidx.compose.ui.Modifier
import com.compose.desktop.native.element.GraphicsLayerModifier

// TransformOrigin lives in its own vendored file (TransformOrigin.kt).
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

/**
 * Upstream ships `CompositingStrategy` as a value class in TWO places:
 * `androidx.compose.ui.graphics.CompositingStrategy` (inside
 * GraphicsLayerModifier.kt) and `androidx.compose.ui.graphics.layer
 * .CompositingStrategy`. Ours here is the graphics-package one — the
 * `GraphicsLayerScope.compositingStrategy` uses it (Int in the project
 * reduced impl becomes a value class here). Same underlying values
 * (0/1/2 = Auto/Offscreen/ModulateAlpha).
 */
@kotlin.jvm.JvmInline
value class CompositingStrategy internal constructor(private val value: Int) {
	companion object {
		val Auto: CompositingStrategy = CompositingStrategy(0)
		val Offscreen: CompositingStrategy = CompositingStrategy(1)
		val ModulateAlpha: CompositingStrategy = CompositingStrategy(2)
	}
}

/**
 * Upstream `ReusableGraphicsLayerScope` — an internal mutable implementation
 * used by NodeCoordinator to compose a GraphicsLayerScope per-layer. Vendored
 * `NodeCoordinator.kt` construct one; we ship the same interface impl.
 */
internal class ReusableGraphicsLayerScope : GraphicsLayerScope {
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

	/** Reset to defaults for reuse across nodes. */
	fun reset() {
		scaleX = 1f; scaleY = 1f; alpha = 1f
		translationX = 0f; translationY = 0f
		shadowElevation = 0f
		ambientShadowColor = Color.Black; spotShadowColor = Color.Black
		rotationX = 0f; rotationY = 0f; rotationZ = 0f
		cameraDistance = 8f
		transformOrigin = TransformOrigin.Center
		shape = RectangleShape
		clip = false
		compositingStrategy = 0
	}

	// Phase 9: NodeCoordinator configures these before recording the layer.
	var graphicsDensity: androidx.compose.ui.unit.Density = androidx.compose.ui.unit.Density(1f)
	var layoutDirection: androidx.compose.ui.unit.LayoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr
	var outline: androidx.compose.ui.graphics.Outline? = null
	fun updateOutline() {}
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
   when the requested layer is a no-op.

   `shape` + `clip` are honoured by lowering to a `ClipModifier` (the
   project's clip element) when `clip == true` — that lets vendored
   upstream `androidx.compose.ui.draw.Clip.kt` resolve to the same code
   path as the project's `Modifier.clip(shape)` did before. */
fun Modifier.graphicsLayer(
	alpha: Float = 1f,
	scaleX: Float = 1f,
	scaleY: Float = 1f,
	rotationZ: Float = 0f,
	translationX: Float = 0f,
	translationY: Float = 0f,
	transformOrigin: TransformOrigin = TransformOrigin.Center,
	shape: Shape = RectangleShape,
	clip: Boolean = false,
	cacheKey: Any? = null,
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
	val vBase = if (vMod.isIdentity) this else then(vMod)
	return if (clip) vBase.then(com.compose.desktop.native.element.ClipModifier(shape)) else vBase
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
