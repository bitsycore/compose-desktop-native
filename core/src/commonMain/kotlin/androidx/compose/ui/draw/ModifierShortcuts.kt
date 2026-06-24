package androidx.compose.ui.draw

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

// ==================
// MARK: Thin transform / draw modifier shortcuts
// ==================

// All of these are one-line wrappers over Modifier.graphicsLayer — same
// semantics as official Compose's individual rotate/scale modifiers (pivot at
// the centre, applied at composite time).

// Rotate this node and its subtree by degrees around its centre.
@Stable
fun Modifier.rotate(degrees: Float): Modifier =
	if (degrees == 0f) this else graphicsLayer(rotationZ = degrees)

// Uniform scale around the centre. scale = 1f is a no-op.
@Stable
fun Modifier.scale(scale: Float): Modifier =
	if (scale == 1f) this else graphicsLayer(scaleX = scale, scaleY = scale)

// Independent X/Y scale around the centre.
@Stable
fun Modifier.scale(scaleX: Float, scaleY: Float): Modifier =
	if (scaleX == 1f && scaleY == 1f) this
	else graphicsLayer(scaleX = scaleX, scaleY = scaleY)
