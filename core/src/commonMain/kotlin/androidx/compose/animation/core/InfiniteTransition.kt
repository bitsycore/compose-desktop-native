package androidx.compose.animation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

// ==================
// MARK: InfiniteTransition
// ==================

/* Coordinates one or more infinite-loop animations on a shared clock so
   they all advance from the same frame timestamp. Each animateFoo() call
   adds a child animation and returns a State<T> whose value updates on
   every frame; the InfiniteTransition's LaunchedEffect drives them all
   via a single withFrameNanos loop. */
class InfiniteTransition internal constructor() {

	private val fChildren = mutableListOf<Child<*>>()
	private var fStartNanos: Long = -1L

	internal class Child<T>(
		val state: androidx.compose.runtime.MutableState<T>,
		val initial: T,
		val target: T,
		val spec: InfiniteRepeatableSpec<T>,
		val lerp: (T, T, Float) -> T,
	)

	internal fun add(inChild: Child<*>) {
		fChildren.add(inChild)
	}

	internal suspend fun run() {
		fStartNanos = withFrameNanos { it }
		while (true) {
			val vNow = withFrameNanos { it }
			val vElapsedMs = ((vNow - fStartNanos) / 1_000_000).toInt()
			for (vC in fChildren) tick(vC, vElapsedMs)
		}
	}

	private fun <T> tick(inC: Child<T>, inElapsedMs: Int) {
		val (vVal, _) = evaluateSpec(inC.spec, inC.initial, inC.target, inElapsedMs, inC.lerp)
		inC.state.value = vVal
	}
}

/* Composition-scoped factory. The same InfiniteTransition instance is
   reused across recompositions; its drive coroutine is launched once. */
@Composable
fun rememberInfiniteTransition(label: String = "InfiniteTransition"): InfiniteTransition {
	val vT = remember { InfiniteTransition() }
	LaunchedEffect(vT) { vT.run() }
	return vT
}

// ==================
// MARK: InfiniteTransition.animateFoo
// ==================

@Composable
fun InfiniteTransition.animateFloat(
	initialValue: Float,
	targetValue: Float,
	animationSpec: InfiniteRepeatableSpec<Float>,
	label: String = "FloatAnimation",
): State<Float> {
	val vState = remember { mutableStateOf(initialValue) }
	remember(initialValue, targetValue) {
		add(InfiniteTransition.Child(vState, initialValue, targetValue, animationSpec) { vA, vB, vF -> vA + (vB - vA) * vF })
	}
	return vState
}

// InfiniteTransition.animateColor lives in androidx.compose.animation (its official
// package); InfiniteTransition.animateDp — a non-official convenience — lives in
// com.compose.desktop.native.animation.
