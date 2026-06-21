package androidx.compose.ui.graphics

import androidx.compose.ui.Modifier

// ==================
// MARK: TransformOrigin
// ==================

/* Pivot point for scale / rotation, expressed as fractions of the node's
   bounds (0,0 = top-left, 1,1 = bottom-right). Default (0.5, 0.5) is the
   centre of the node. */
data class TransformOrigin(val pivotFractionX: Float, val pivotFractionY: Float) {
	companion object {
		val Center = TransformOrigin(0.5f, 0.5f)
		val TopLeft = TransformOrigin(0f, 0f)
	}
}

// ==================
// MARK: GraphicsLayerModifier
// ==================

/* A "graphics layer" element: alpha + 2D transform (scale / rotation /
   translation), with an optional cacheKey that opts the subtree into
   render-to-texture caching across frames.

   Caching semantics:
   - cacheKey == null  -> no caching; the subtree is drawn every frame, but
     transforms / alpha still apply via the renderer's transform stack.
   - cacheKey != null  -> the renderer renders the subtree into an offscreen
     target on the first frame, stores it keyed by (node, cacheKey), and
     reuses the target while the key compares equal. Any state in the
     subtree must therefore be reflected in the key — change the key to
     invalidate the cache.

   needsLayer means the renderer must always promote the subtree to an
   offscreen layer for this frame (because of alpha or because we're
   caching). needsTransform means a non-identity scale / rotation /
   translation has been requested. */
data class GraphicsLayerModifier(
	val alpha: Float = 1f,
	val scaleX: Float = 1f,
	val scaleY: Float = 1f,
	val rotationZ: Float = 0f,
	val translationX: Float = 0f,
	val translationY: Float = 0f,
	val transformOrigin: TransformOrigin = TransformOrigin.Center,
	val cacheKey: Any? = null,
) : Modifier.Element {

	val needsLayer: Boolean
		get() = alpha < 1f || cacheKey != null

	val needsTransform: Boolean
		get() = scaleX != 1f || scaleY != 1f || rotationZ != 0f ||
			translationX != 0f || translationY != 0f

	val isIdentity: Boolean
		get() = !needsLayer && !needsTransform
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
