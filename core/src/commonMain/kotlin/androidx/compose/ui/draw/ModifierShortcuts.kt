package androidx.compose.ui.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerModifier
import androidx.compose.ui.graphics.graphicsLayer

// ==================
// MARK: Thin transform / draw modifier shortcuts
// ==================

/* All of these are one-line wrappers over Modifier.graphicsLayer — same
   semantics as upstream Compose's individual rotate/scale/translate
   modifiers (pivot at the centre, applied at composite time). */

/* Rotate this node and its subtree by inDegrees around its centre. */
fun Modifier.rotate(inDegrees: Float): Modifier =
	if (inDegrees == 0f) this else graphicsLayer(rotationZ = inDegrees)

/* Uniform scale around the centre. inScale = 1f is a no-op. */
fun Modifier.scale(inScale: Float): Modifier =
	if (inScale == 1f) this else graphicsLayer(scaleX = inScale, scaleY = inScale)

/* Independent X/Y scale around the centre. */
fun Modifier.scale(inScaleX: Float, inScaleY: Float): Modifier =
	if (inScaleX == 1f && inScaleY == 1f) this
	else graphicsLayer(scaleX = inScaleX, scaleY = inScaleY)

/* Visually shift this node and its subtree by (inX, inY) without
   affecting layout. Useful for nudging without rewriting the parent. */
fun Modifier.translate(inX: Float, inY: Float): Modifier =
	if (inX == 0f && inY == 0f) this
	else graphicsLayer(translationX = inX, translationY = inY)

// ==================
// MARK: zIndex
// ==================

/* Override draw ordering within a parent. Higher z draws on top of
   lower z; siblings without zIndex have implicit z = 0. The renderer
   sorts children by zIndex right before painting them. */
data class ZIndexModifier(val zIndex: Float) : Modifier.Element

fun Modifier.zIndex(inZ: Float): Modifier =
	if (inZ == 0f) this else this.then(ZIndexModifier(inZ))
