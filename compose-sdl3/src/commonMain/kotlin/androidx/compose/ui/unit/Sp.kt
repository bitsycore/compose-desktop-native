package androidx.compose.ui.unit

// ==================
// MARK: Sp
// ==================

/* Scale-independent pixels. In real Compose this is part of a TextUnit
   that also supports em; we only need sp for now. Without an explicit
   density factor, 1.sp == 1.dp == 1px in this build. */
value class Sp(val value: Float) : Comparable<Sp> {
    override fun compareTo(other: Sp) = value.compareTo(other.value)

    operator fun plus(other: Sp) = Sp(value + other.value)
    operator fun minus(other: Sp) = Sp(value - other.value)
    operator fun times(factor: Float) = Sp(value * factor)
    operator fun times(factor: Int) = Sp(value * factor)
    operator fun div(factor: Float) = Sp(value / factor)
    operator fun div(other: Sp) = value / other.value
    operator fun unaryMinus() = Sp(-value)

    companion object {
        val Zero = Sp(0f)
        val Unspecified = Sp(Float.NaN)
    }
}

inline val Int.sp get() = Sp(this.toFloat())
inline val Float.sp get() = Sp(this)
inline val Double.sp get() = Sp(this.toFloat())
