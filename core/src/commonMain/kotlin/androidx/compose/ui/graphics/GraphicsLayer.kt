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
