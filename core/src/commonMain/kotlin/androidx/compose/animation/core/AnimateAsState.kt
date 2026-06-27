package androidx.compose.animation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: animate*AsState
// ==================

/* The bread-and-butter Compose API: returns a State<T> that animates
   smoothly from its current value to whatever you pass as targetValue.
   When targetValue changes, a new animation kicks off (cancelling any
   in-flight one). The runtime driver is plain withFrameNanos on the
   composition's MonotonicFrameClock — same one Row / Column use. */

@Composable
fun animateFloatAsState(
	targetValue: Float,
	animationSpec: AnimationSpec<Float> = spring(),
	visibilityThreshold: Float = 0.01f,
	label: String = "FloatAnimation",
	finishedListener: ((Float) -> Unit)? = null,
): State<Float> = animateValueAsState(
	targetValue, animationSpec, label, finishedListener,
) { vA, vB, vF -> vA + (vB - vA) * vF }

@Composable
fun animateIntAsState(
	targetValue: Int,
	animationSpec: AnimationSpec<Int> = spring(),
	label: String = "IntAnimation",
	finishedListener: ((Int) -> Unit)? = null,
): State<Int> = animateValueAsState(
	targetValue, animationSpec, label, finishedListener,
) { vA, vB, vF -> (vA + (vB - vA) * vF).toInt() }

@Composable
fun animateDpAsState(
	targetValue: Dp,
	animationSpec: AnimationSpec<Dp> = spring(),
	label: String = "DpAnimation",
	finishedListener: ((Dp) -> Unit)? = null,
): State<Dp> = animateValueAsState(
	targetValue, animationSpec, label, finishedListener,
) { vA, vB, vF -> (vA.value + (vB.value - vA.value) * vF).dp }

/* Generic primitive the public animateFooAsState helpers wrap with the right
   lerp. Internal — the official animateValueAsState takes a TwoWayConverter
   (which this lerp-lambda stand-in deliberately omits), so it isn't exposed as
   official public API here. */
@Composable
internal fun <T> animateValueAsState(
	targetValue: T,
	animationSpec: AnimationSpec<T> = spring(),
	label: String = "ValueAnimation",
	finishedListener: ((T) -> Unit)? = null,
	lerp: (T, T, Float) -> T,
): State<T> {
	val vState = remember { mutableStateOf(targetValue) }
	LaunchedEffect(targetValue) {
		val vFrom = vState.value
		val vTo = targetValue
		if (vFrom == vTo) return@LaunchedEffect
		val vStartNanos = withFrameNanos { it }
		while (true) {
			val vNow = withFrameNanos { it }
			val vElapsedMs = ((vNow - vStartNanos) / 1_000_000).toInt()
			val (vV, vDone) = evaluateSpec(animationSpec, vFrom, vTo, vElapsedMs, lerp)
			vState.value = vV
			if (vDone) {
				finishedListener?.invoke(vV)
				return@LaunchedEffect
			}
		}
	}
	return vState
}

/* Linear interpolation between two Colors in straight-alpha RGB. Not
   gamma-correct (would need sRGB → linear → sRGB) but matches the
   default upstream behaviour for animateColorAsState. */
internal fun lerpColor(inA: Color, inB: Color, inF: Float): Color {
	val vF = inF.coerceIn(0f, 1f)
	val vR = inA.red   + (inB.red   - inA.red)   * vF
	val vG = inA.green + (inB.green - inA.green) * vF
	val vBl = inA.blue + (inB.blue  - inA.blue)  * vF
	val vAl = inA.alpha + (inB.alpha - inA.alpha) * vF
	return Color(vR, vG, vBl, vAl)
}
