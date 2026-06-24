package androidx.compose.ui.unit

import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: Dp
// ==================

value class Dp(val value: Float) : Comparable<Dp> {
    // NaN (Dp.Unspecified) is incomparable: return 0 so it is neither < nor >.
    override fun compareTo(other: Dp): Int =
        if (value.isNaN() || other.value.isNaN()) 0 else value.compareTo(other.value)

    operator fun plus(other: Dp) = Dp(value + other.value)
    operator fun minus(other: Dp) = Dp(value - other.value)
    operator fun times(other: Float) = Dp(value * other)
    operator fun times(other: Int) = Dp(value * other)
    operator fun div(other: Float) = Dp(value / other)
    operator fun div(other: Int) = Dp(value / other)
    operator fun div(other: Dp) = value / other.value
    operator fun unaryMinus() = Dp(-value)

    companion object {
        val Hairline = Dp(0f)
        val Infinity = Dp(Float.POSITIVE_INFINITY)
        val Unspecified = Dp(Float.NaN)
    }
}

inline val Int.dp get() = Dp(this.toFloat())
inline val Float.dp get() = Dp(this)
inline val Double.dp get() = Dp(this.toFloat())

// ==================
// MARK: Dp helpers (official surface)
// ==================

inline operator fun Int.times(other: Dp) = Dp(this * other.value)
inline operator fun Float.times(other: Dp) = Dp(this * other.value)
inline operator fun Double.times(other: Dp) = Dp(this.toFloat() * other.value)

fun min(a: Dp, b: Dp): Dp = Dp(min(a.value, b.value))
fun max(a: Dp, b: Dp): Dp = Dp(max(a.value, b.value))

fun Dp.coerceIn(minimumValue: Dp, maximumValue: Dp): Dp =
    Dp(value.coerceIn(minimumValue.value, maximumValue.value))
fun Dp.coerceAtLeast(minimumValue: Dp): Dp = Dp(value.coerceAtLeast(minimumValue.value))
fun Dp.coerceAtMost(maximumValue: Dp): Dp = Dp(value.coerceAtMost(maximumValue.value))

fun lerp(start: Dp, stop: Dp, fraction: Float): Dp =
    Dp(start.value + (stop.value - start.value) * fraction)

val Dp.isSpecified: Boolean get() = !value.isNaN()
val Dp.isUnspecified: Boolean get() = value.isNaN()
val Dp.isFinite: Boolean get() = value != Float.POSITIVE_INFINITY
inline fun Dp.takeOrElse(block: () -> Dp): Dp = if (isSpecified) this else block()

// ==================
// MARK: DpSize / DpOffset
// ==================

data class DpSize(val width: Dp, val height: Dp) {
    companion object {
        val Zero = DpSize(0.dp, 0.dp)
        val Unspecified = DpSize(Dp.Unspecified, Dp.Unspecified)
    }
}

data class DpOffset(val x: Dp, val y: Dp) {
    companion object {
        val Zero = DpOffset(0.dp, 0.dp)
        val Unspecified = DpOffset(Dp.Unspecified, Dp.Unspecified)
    }
}
