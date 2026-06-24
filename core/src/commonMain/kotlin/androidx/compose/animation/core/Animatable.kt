package androidx.compose.animation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException

// ==================
// MARK: Animatable
// ==================

/* Imperative animation handle. Holds a state-backed `value` and lets
   callers `snapTo(target)` (instant) or `animateTo(target, spec)`
   (suspend until the spec finishes). Concurrent animateTo calls cancel
   the previous one, like the upstream API. */
class Animatable<T>(
	initialValue: T,
	private val lerp: (T, T, Float) -> T,
) {
	private var fValue by mutableStateOf(initialValue)
	private var fJobId: Int = 0  // bumped on every animateTo to cancel prior runs

	val value: T get() = fValue
	var targetValue: T = initialValue
		private set
	var isRunning: Boolean = false
		private set

	/* Jump to the target without animating. Cancels any in-flight
	   animateTo. */
	suspend fun snapTo(targetValue: T) {
		fJobId++
		fValue = targetValue
		this.targetValue = targetValue
		isRunning = false
	}

	/* Animate from the current value to targetValue using the given spec.
	   Suspends until the animation completes or is superseded by a
	   subsequent call (or coroutine cancellation). */
	suspend fun animateTo(
		targetValue: T,
		animationSpec: AnimationSpec<T> = spring(),
	): AnimationResult<T> {
		fJobId++
		val myId = fJobId
		val from = fValue
		this.targetValue = targetValue
		isRunning = true

		val startNanos = withFrameNanos { it }
		try {
			while (true) {
				val now = withFrameNanos { it }
				if (fJobId != myId) return AnimationResult(fValue, AnimationEndReason.BoundReached)
				val elapsedMs = ((now - startNanos) / 1_000_000).toInt()
				val (v, done) = evaluateSpec(animationSpec, from, targetValue, elapsedMs, lerp)
				fValue = v
				if (done) {
					isRunning = false
					return AnimationResult(v, AnimationEndReason.Finished)
				}
			}
		} catch (t: CancellationException) {
			isRunning = false
			throw t
		}
	}

	/* Stop without snapping; current `value` stays where it is. */
	fun stop() {
		fJobId++
		isRunning = false
	}
}

enum class AnimationEndReason { BoundReached, Finished }

data class AnimationResult<T>(val endValue: T, val endReason: AnimationEndReason)

// ==================
// MARK: Composable factories
// ==================

@Composable
fun Animatable(initialValue: Float): Animatable<Float> =
	remember { Animatable(initialValue) { a, b, f -> a + (b - a) * f } }
