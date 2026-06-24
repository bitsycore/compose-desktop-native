package androidx.compose.animation.core

// ==================
// MARK: AnimationSpec
// ==================

/* Specifies HOW a value transitions between two endpoints. Implementations
   compute the current value at a given elapsed time. We model it as a
   thin sealed family so animateFloatAsState / Animatable can switch on
   concrete types in their drive loops (no generic vector math needed —
   we just lerp between two T values via a caller-supplied lerp lambda). */
sealed interface AnimationSpec<T>

// ============
//  TweenSpec — duration + easing

data class TweenSpec<T>(
	val durationMillis: Int = 300,
	val delay: Int = 0,
	val easing: Easing = FastOutSlowInEasing,
) : AnimationSpec<T>

fun <T> tween(
	durationMillis: Int = 300,
	delayMillis: Int = 0,
	easing: Easing = FastOutSlowInEasing,
): TweenSpec<T> = TweenSpec(durationMillis, delayMillis, easing)

// ============
//  SnapSpec — instant jump (optional delay)

data class SnapSpec<T>(val delay: Int = 0) : AnimationSpec<T>

fun <T> snap(delayMillis: Int = 0): SnapSpec<T> = SnapSpec(delayMillis)

// ============
//  Spring — critically-damped spring approximation. Not a true physics
//  spring (no velocity-aware overshoot); duration is derived from
//  stiffness so it behaves close enough for typical UI accents.

data class SpringSpec<T>(
	val dampingRatio: Float = Spring.DampingRatioNoBouncy,
	val stiffness: Float = Spring.StiffnessMedium,
	val visibilityThreshold: T? = null,
) : AnimationSpec<T>

object Spring {
	const val DampingRatioNoBouncy = 1f
	const val DampingRatioLowBouncy = 0.75f
	const val DampingRatioMediumBouncy = 0.5f
	const val DampingRatioHighBouncy = 0.2f

	const val StiffnessHigh = 10_000f
	const val StiffnessMedium = 1500f
	const val StiffnessMediumLow = 400f
	const val StiffnessLow = 200f
	const val StiffnessVeryLow = 50f

	const val DefaultDisplacementThreshold = 0.01f
}

fun <T> spring(
	dampingRatio: Float = Spring.DampingRatioNoBouncy,
	stiffness: Float = Spring.StiffnessMedium,
	visibilityThreshold: T? = null,
): SpringSpec<T> = SpringSpec(dampingRatio, stiffness, visibilityThreshold)

// ============
//  Repeatable + InfiniteRepeatable

enum class RepeatMode { Restart, Reverse }

data class RepeatableSpec<T>(
	val iterations: Int,
	val animation: TweenSpec<T>,
	val repeatMode: RepeatMode = RepeatMode.Restart,
) : AnimationSpec<T>

fun <T> repeatable(
	iterations: Int,
	animation: TweenSpec<T>,
	repeatMode: RepeatMode = RepeatMode.Restart,
): RepeatableSpec<T> = RepeatableSpec(iterations, animation, repeatMode)

data class InfiniteRepeatableSpec<T>(
	val animation: TweenSpec<T>,
	val repeatMode: RepeatMode = RepeatMode.Restart,
) : AnimationSpec<T>

fun <T> infiniteRepeatable(
	animation: TweenSpec<T>,
	repeatMode: RepeatMode = RepeatMode.Restart,
): InfiniteRepeatableSpec<T> = InfiniteRepeatableSpec(animation, repeatMode)

// ==================
// MARK: Spec evaluation helpers
// ==================

/* Drives a spec from `inInitial` to `inTarget` at elapsed time `inElapsedMs`.
   Returns (currentValue, isFinished). lerp must produce a value at fraction
   ∈ [0..1]. Caller is expected to stop once isFinished is true. */
internal fun <T> evaluateSpec(
	inSpec: AnimationSpec<T>,
	inInitial: T,
	inTarget: T,
	inElapsedMs: Int,
	inLerp: (T, T, Float) -> T,
): Pair<T, Boolean> = when (inSpec) {
	is TweenSpec<T>             -> evalTween(inSpec, inInitial, inTarget, inElapsedMs, inLerp)
	is SnapSpec<T>              -> evalSnap(inSpec, inInitial, inTarget, inElapsedMs, inLerp)
	is SpringSpec<T>            -> evalSpring(inSpec, inInitial, inTarget, inElapsedMs, inLerp)
	is RepeatableSpec<T>        -> evalRepeatable(inSpec, inInitial, inTarget, inElapsedMs, inLerp)
	is InfiniteRepeatableSpec<T> -> evalInfinite(inSpec, inInitial, inTarget, inElapsedMs, inLerp)
}

private fun <T> evalTween(
	inSpec: TweenSpec<T>, inA: T, inB: T, inT: Int, inLerp: (T, T, Float) -> T,
): Pair<T, Boolean> {
	val vEff = inT - inSpec.delay
	if (vEff <= 0) return inA to false
	if (inSpec.durationMillis <= 0) return inB to true
	if (vEff >= inSpec.durationMillis) return inB to true
	val vFrac = inSpec.easing.transform(vEff.toFloat() / inSpec.durationMillis)
	return inLerp(inA, inB, vFrac) to false
}

private fun <T> evalSnap(
	inSpec: SnapSpec<T>, inA: T, inB: T, inT: Int, @Suppress("UNUSED_PARAMETER") inLerp: (T, T, Float) -> T,
): Pair<T, Boolean> = if (inT < inSpec.delay) inA to false else inB to true

/* Critically-damped spring approximation: derive an effective duration
   from stiffness, then run a smoothstep-ish easing. Not physically
   accurate but covers the common UI use of "a spring with bounce". */
private fun <T> evalSpring(
	inSpec: SpringSpec<T>, inA: T, inB: T, inT: Int, inLerp: (T, T, Float) -> T,
): Pair<T, Boolean> {
	// Roughly: higher stiffness → shorter duration. Bounce comes from
	// dampingRatio < 1 producing a sine ripple after the main curve.
	val vDuration = (4500f / inSpec.stiffness * 60f).toInt().coerceAtLeast(50)
	if (inT >= vDuration) return inB to true
	val vF = (inT.toFloat() / vDuration).coerceIn(0f, 1f)
	val vBase = 1f - kotlin.math.exp(-6f * vF * inSpec.dampingRatio)
	val vBounce = if (inSpec.dampingRatio < 1f) {
		val vAmp = (1f - inSpec.dampingRatio) * 0.3f
		vAmp * kotlin.math.exp(-3f * vF) * kotlin.math.sin(12f * vF)
	} else 0f
	val vFrac = (vBase + vBounce).coerceIn(0f, 1.5f)
	return inLerp(inA, inB, vFrac) to false
}

private fun <T> evalRepeatable(
	inSpec: RepeatableSpec<T>, inA: T, inB: T, inT: Int, inLerp: (T, T, Float) -> T,
): Pair<T, Boolean> {
	val vCycle = inSpec.animation.durationMillis + inSpec.animation.delay
	if (vCycle <= 0) return inB to true
	val vTotal = vCycle * inSpec.iterations
	if (inT >= vTotal) return inB to true
	val vIdx = inT / vCycle
	val vLocal = inT % vCycle
	val vRev = inSpec.repeatMode == RepeatMode.Reverse && (vIdx % 2 == 1)
	val vFrom = if (vRev) inB else inA
	val vTo = if (vRev) inA else inB
	return evalTween(inSpec.animation, vFrom, vTo, vLocal, inLerp)
}

private fun <T> evalInfinite(
	inSpec: InfiniteRepeatableSpec<T>, inA: T, inB: T, inT: Int, inLerp: (T, T, Float) -> T,
): Pair<T, Boolean> {
	val vCycle = inSpec.animation.durationMillis + inSpec.animation.delay
	if (vCycle <= 0) return inA to false
	val vIdx = inT / vCycle
	val vLocal = inT % vCycle
	val vRev = inSpec.repeatMode == RepeatMode.Reverse && (vIdx % 2 == 1)
	val vFrom = if (vRev) inB else inA
	val vTo = if (vRev) inA else inB
	val (vVal, _) = evalTween(inSpec.animation, vFrom, vTo, vLocal, inLerp)
	return vVal to false  // never finishes
}
