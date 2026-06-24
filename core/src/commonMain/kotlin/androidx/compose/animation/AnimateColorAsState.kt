package androidx.compose.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.lerpColor
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color

// ==================
// MARK: animateColorAsState
// ==================

/* Animates between Colors. Lives in androidx.compose.animation (not .core) to
   match official Compose. */
@Composable
fun animateColorAsState(
	targetValue: Color,
	animationSpec: AnimationSpec<Color> = spring(),
	label: String = "ColorAnimation",
	finishedListener: ((Color) -> Unit)? = null,
): State<Color> = animateValueAsState(
	targetValue, animationSpec, label, finishedListener,
) { a, b, f -> lerpColor(a, b, f) }
