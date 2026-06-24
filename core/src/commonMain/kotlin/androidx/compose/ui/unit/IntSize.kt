package androidx.compose.ui.unit

import kotlin.math.roundToInt

// ==================
// MARK: IntSize
// ==================

data class IntSize(val width: Int, val height: Int) {
    operator fun times(other: Int) = IntSize(width * other, height * other)
    operator fun div(other: Int) = IntSize(width / other, height / other)

    companion object {
        val Zero = IntSize(0, 0)
    }
}

operator fun Int.times(size: IntSize) = size * this

// ==================
// MARK: IntOffset
// ==================

data class IntOffset(val x: Int, val y: Int) {
    operator fun plus(other: IntOffset) = IntOffset(x + other.x, y + other.y)
    operator fun minus(other: IntOffset) = IntOffset(x - other.x, y - other.y)
    operator fun times(operand: Float) = IntOffset((x * operand).roundToInt(), (y * operand).roundToInt())
    operator fun div(operand: Float) = IntOffset((x / operand).roundToInt(), (y / operand).roundToInt())
    operator fun unaryMinus() = IntOffset(-x, -y)

    companion object {
        val Zero = IntOffset(0, 0)
        val Max = IntOffset(Int.MAX_VALUE, Int.MAX_VALUE)
    }
}
