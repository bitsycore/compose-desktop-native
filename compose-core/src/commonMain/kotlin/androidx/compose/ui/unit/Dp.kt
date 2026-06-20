package androidx.compose.ui.unit

// ==================
// MARK: Dp
// ==================

value class Dp(val value: Float) : Comparable<Dp> {
    override fun compareTo(other: Dp) = value.compareTo(other.value)

    operator fun plus(other: Dp) = Dp(value + other.value)
    operator fun minus(other: Dp) = Dp(value - other.value)
    operator fun times(factor: Float) = Dp(value * factor)
    operator fun times(factor: Int) = Dp(value * factor)
    operator fun div(factor: Float) = Dp(value / factor)
    operator fun div(other: Dp) = value / other.value
    operator fun unaryMinus() = Dp(-value)

    companion object {
        val Zero = Dp(0f)
        val Infinity = Dp(Float.MAX_VALUE)
        val Unspecified = Dp(Float.NaN)
        val Hairline = Dp(0f)
    }
}

inline val Int.dp get() = Dp(this.toFloat())
inline val Float.dp get() = Dp(this)
inline val Double.dp get() = Dp(this.toFloat())

// ==================
// MARK: DpSize / DpOffset
// ==================

data class DpSize(val width: Dp, val height: Dp) {
    companion object {
        val Zero = DpSize(Dp.Zero, Dp.Zero)
        val Unspecified = DpSize(Dp.Unspecified, Dp.Unspecified)
    }
}

data class DpOffset(val x: Dp, val y: Dp) {
    companion object {
        val Zero = DpOffset(Dp.Zero, Dp.Zero)
        val Unspecified = DpOffset(Dp.Unspecified, Dp.Unspecified)
    }
}
