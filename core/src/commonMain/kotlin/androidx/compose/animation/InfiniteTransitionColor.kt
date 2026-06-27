package androidx.compose.animation

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.lerpColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// ==================
// MARK: InfiniteTransition.animateColor
// ==================

/* Color leg of an InfiniteTransition. Lives in androidx.compose.animation (its
   official package — animation-core has no dependency on ui.graphics.Color, so
   upstream keeps the Color/Dp overloads in the animation module). */
@Composable
fun InfiniteTransition.animateColor(
	initialValue: Color,
	targetValue: Color,
	animationSpec: InfiniteRepeatableSpec<Color>,
	label: String = "ColorAnimation",
): State<Color> {
	val vState = remember { mutableStateOf(initialValue) }
	remember(initialValue, targetValue) {
		add(InfiniteTransition.Child(vState, initialValue, targetValue, animationSpec) { vA, vB, vF -> lerpColor(vA, vB, vF) })
	}
	return vState
}
