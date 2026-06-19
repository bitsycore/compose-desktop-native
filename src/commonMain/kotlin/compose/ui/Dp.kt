package compose.ui

// ==================
// MARK: Dp & Density
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
    }
}

val Int.dp get() = Dp(this.toFloat())
val Float.dp get() = Dp(this)
val Double.dp get() = Dp(this.toFloat())

data class Density(val density: Float = 1f) {
    fun Dp.toPx() = value * density
    fun Float.toDp() = Dp(this / density)
    fun Int.toDp() = Dp(this.toFloat() / density)
}
