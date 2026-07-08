package androidx.navigation3.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry

/*
 Minimal NavDisplay for the SDL/native stack — a reimpl of
 androidx.navigation3.ui.NavDisplay (whose non-Android upstream is NotImplemented,
 with no K/N desktop artifact). Renders the LAST entry of [backStack], resolved via
 [entryProvider], animating between entries by their contentKey. Scenes /
 SceneStrategy / entry decorators / predictive-back from the full artifact are not
 modelled — this covers single-pane navigation, which is the common desktop case.

 Back navigation is the caller's job (pop [backStack] — a navigation3-runtime
 NavBackStack IS a MutableList).
*/
@Composable
public fun <T : Any> NavDisplay(
	backStack: List<T>,
	modifier: Modifier = Modifier,
	contentAlignment: Alignment = Alignment.TopStart,
	transitionSpec: AnimatedContentTransitionScope<NavEntry<T>>.() -> ContentTransform = {
		fadeIn(tween(220)) togetherWith fadeOut(tween(160))
	},
	entryProvider: (T) -> NavEntry<T>,
) {
	val top = backStack.lastOrNull() ?: return
	AnimatedContent(
		targetState = entryProvider(top),
		modifier = modifier,
		transitionSpec = transitionSpec,
		contentAlignment = contentAlignment,
		contentKey = { it.contentKey },
		label = "NavDisplay",
	) { entry -> entry.Content() }
}
