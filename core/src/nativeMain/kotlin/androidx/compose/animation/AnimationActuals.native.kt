package androidx.compose.animation

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density

// Native actuals for animation-module expects that upstream only ships for
// android / nonAndroid (where nonAndroid still routes through Android's
// ViewConfiguration). On native we don't have ViewConfiguration so use the
// Android default friction value (0.015f, from ViewConfiguration).

internal actual val platformFlingScrollFriction: Float = 0.015f

@Composable
public actual fun <T> rememberSplineBasedDecay(): DecayAnimationSpec<T> {
	// Native doesn't have a LocalDensity CompositionLocal yet, so we use a
	// fixed Density(1f). The renderer applies HiDPI scaling separately, and
	// fling decay is computed in logical points either way.
	val density = remember { Density(1f) }
	return remember { SplineBasedFloatDecayAnimationSpec(density).generateDecayAnimationSpec() }
}
