package androidx.compose.ui.unit

// ==================
// MARK: IntSize
// ==================

data class IntSize(val width: Int, val height: Int) {
    companion object {
        val Zero = IntSize(0, 0)
    }
}

// ==================
// MARK: IntOffset
// ==================

data class IntOffset(val x: Int, val y: Int) {
    operator fun plus(other: IntOffset) = IntOffset(x + other.x, y + other.y)
    operator fun minus(other: IntOffset) = IntOffset(x - other.x, y - other.y)

    companion object {
        val Zero = IntOffset(0, 0)
    }
}
