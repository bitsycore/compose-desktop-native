package androidx.compose.animation.core

// ==================
// MARK: Easing
// ==================

/* Maps [0..1] linear progress to [0..1] eased progress. Standard upstream
   interface; predefined curves match Material-spec defaults so animations
   "feel right" out of the box. */
fun interface Easing {
	fun transform(fraction: Float): Float
}

val LinearEasing: Easing = Easing { it }

/* Material standard easing: starts fast, slows into the target. The
   coefficients match upstream `FastOutSlowInEasing`. */
val FastOutSlowInEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/* Outgoing motion (entering / leaving the viewport): accelerate then
   cruise. Matches upstream `FastOutLinearInEasing`. */
val FastOutLinearInEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/* Incoming motion (settling into place): cruise then slow. Matches
   upstream `LinearOutSlowInEasing`. */
val LinearOutSlowInEasing: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/* Cubic Bézier easing with control points (a, b) and (c, d). Solved by
   bisection on the parameter t given x = fraction (the Bézier is
   parameterised in t, not x, so we have to invert numerically — same
   technique upstream uses). 8 iterations gives sub-pixel error for any
   realistic animation. */
class CubicBezierEasing(
	private val a: Float,
	private val b: Float,
	private val c: Float,
	private val d: Float,
) : Easing {

	override fun transform(fraction: Float): Float {
		if (fraction <= 0f) return 0f
		if (fraction >= 1f) return 1f
		val t = solveT(fraction)
		return bezier(b, d, t)
	}

	// Solve x(t) = x for t ∈ [0, 1] via bisection.
	private fun solveT(x: Float): Float {
		var lo = 0f; var hi = 1f
		repeat(8) {
			val mid = (lo + hi) * 0.5f
			val xMid = bezier(a, c, mid)
			if (xMid < x) lo = mid else hi = mid
		}
		return (lo + hi) * 0.5f
	}

	// 1D cubic Bézier with implicit P0=0, P3=1 and explicit P1, P2.
	private fun bezier(p1: Float, p2: Float, t: Float): Float {
		val one = 1f - t
		return (3f * one * one * t * p1) +
		       (3f * one * t * t * p2) +
		       (t * t * t)
	}
}
