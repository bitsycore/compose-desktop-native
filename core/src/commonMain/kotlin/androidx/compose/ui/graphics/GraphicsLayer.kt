package androidx.compose.ui.graphics

import androidx.compose.ui.Modifier
import com.compose.desktop.native.element.GraphicsLayerModifier

// GraphicsLayerScope + ReusableGraphicsLayerScope + DefaultCameraDistance are now
// VENDORED (androidx/compose/ui/graphics/GraphicsLayerScope.kt). This file keeps only
// the project-side extras: CompositingStrategy (the ui.graphics-package value class the
// vendored GraphicsLayerScope.compositingStrategy resolves to — distinct from the
// vendored ui.graphics.layer.CompositingStrategy) and the Modifier.graphicsLayer factories.

// ==================
// MARK: CompositingStrategy
// ==================

/*
 Upstream ships `CompositingStrategy` as a value class in two packages:
 `androidx.compose.ui.graphics.CompositingStrategy` (used by GraphicsLayerScope, here)
 and `androidx.compose.ui.graphics.layer.CompositingStrategy` (vendored, used by the
 GraphicsLayer engine). Same underlying values (0/1/2 = Auto/Offscreen/ModulateAlpha).
*/
@kotlin.jvm.JvmInline
value class CompositingStrategy internal constructor(private val value: Int) {
	companion object {
		val Auto: CompositingStrategy = CompositingStrategy(0)
		val Offscreen: CompositingStrategy = CompositingStrategy(1)
		val ModulateAlpha: CompositingStrategy = CompositingStrategy(2)
	}
}

// ==================
// MARK: Modifier.graphicsLayer
// ==================

/* Apply a graphics layer to this node and its subtree. See GraphicsLayerModifier for
   caching semantics. Returns `this` unchanged when the requested layer is a no-op.
   `shape` + `clip` lower to a ClipModifier when clip == true. */
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

/* Block-form Modifier.graphicsLayer. Populates the vendored ReusableGraphicsLayerScope
   via the block, then folds the snapshot into a GraphicsLayerModifier. The renderer reads
   alpha / scale / translation / rotation / origin from the element; the other scope
   properties (shape, clip, shadow, renderEffect, …) are accept-and-ignore today. */
fun Modifier.graphicsLayer(block: GraphicsLayerScope.() -> Unit): Modifier {
	val vScope = ReusableGraphicsLayerScope().apply(block)
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
