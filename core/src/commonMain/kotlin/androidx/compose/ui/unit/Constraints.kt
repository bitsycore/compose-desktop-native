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

    companion object {
        const val Infinity = Int.MAX_VALUE

        fun fixed(width: Int, height: Int) = Constraints(width, width, height, height)
        fun fixedWidth(width: Int) = Constraints(minWidth = width, maxWidth = width)
        fun fixedHeight(height: Int) = Constraints(minHeight = height, maxHeight = height)
    }
}

// ==================
// MARK: Constraints helpers (official surface)
// ==================

fun Constraints.constrain(size: IntSize): IntSize =
    IntSize(size.width.coerceIn(minWidth, maxWidth), size.height.coerceIn(minHeight, maxHeight))

fun Constraints.constrain(otherConstraints: Constraints): Constraints =
    Constraints(
        minWidth = otherConstraints.minWidth.coerceIn(minWidth, maxWidth),
        maxWidth = otherConstraints.maxWidth.coerceIn(minWidth, maxWidth),
        minHeight = otherConstraints.minHeight.coerceIn(minHeight, maxHeight),
        maxHeight = otherConstraints.maxHeight.coerceIn(minHeight, maxHeight),
    )

fun Constraints.constrainWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)
fun Constraints.constrainHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

fun Constraints.isSatisfiedBy(size: IntSize): Boolean =
    size.width in minWidth..maxWidth && size.height in minHeight..maxHeight

fun Constraints.offset(horizontal: Int = 0, vertical: Int = 0): Constraints {
    fun addMax(value: Int, add: Int) =
        if (value == Constraints.Infinity) value else (value + add).coerceAtLeast(0)
    return Constraints(
        minWidth = (minWidth + horizontal).coerceAtLeast(0),
        maxWidth = addMax(maxWidth, horizontal),
        minHeight = (minHeight + vertical).coerceAtLeast(0),
        maxHeight = addMax(maxHeight, vertical),
    )
}
