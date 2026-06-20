package androidx.compose.ui.draw

import androidx.compose.ui.AlphaModifier
import androidx.compose.ui.Modifier

// ==================
// MARK: Modifier.alpha
// ==================

/* Draws the node and its entire subtree at the given opacity (0f..1f). The
   renderer composites the subtree as one layer, so overlapping children fade
   together rather than each blending separately. alpha = 1f is a no-op. */
fun Modifier.alpha(alpha: Float): Modifier =
	if (alpha >= 1f) this else then(AlphaModifier(alpha.coerceIn(0f, 1f)))
