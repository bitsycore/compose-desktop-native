package androidx.compose.ui.unit

// ==================
// MARK: Constraints
// ==================

data class Constraints(
    val minWidth: Int = 0,
    val maxWidth: Int = Infinity,
    val minHeight: Int = 0,
    val maxHeight: Int = Infinity
) {
    val hasBoundedWidth get() = maxWidth < Infinity
    val hasBoundedHeight get() = maxHeight < Infinity
    val hasFixedWidth get() = minWidth == maxWidth
    val hasFixedHeight get() = minHeight == maxHeight

    fun constrain(width: Int, height: Int) = IntSize(
        width.coerceIn(minWidth, maxWidth),
        height.coerceIn(minHeight, maxHeight)
    )

    companion object {
        const val Infinity = Int.MAX_VALUE

        fun fixed(width: Int, height: Int) = Constraints(width, width, height, height)
        fun fixedWidth(width: Int) = Constraints(minWidth = width, maxWidth = width)
        fun fixedHeight(height: Int) = Constraints(minHeight = height, maxHeight = height)
    }
}
