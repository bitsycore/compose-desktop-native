package compose.ui

// ==================
// MARK: Alignment
// ==================

fun interface Alignment {
    fun align(size: IntSize, space: IntSize): IntOffset

    companion object {
        val TopStart = Alignment { size, space ->
            IntOffset(0, 0)
        }
        val TopCenter = Alignment { size, space ->
            IntOffset((space.width - size.width) / 2, 0)
        }
        val TopEnd = Alignment { size, space ->
            IntOffset(space.width - size.width, 0)
        }
        val CenterStart = Alignment { size, space ->
            IntOffset(0, (space.height - size.height) / 2)
        }
        val Center = Alignment { size, space ->
            IntOffset((space.width - size.width) / 2, (space.height - size.height) / 2)
        }
        val CenterEnd = Alignment { size, space ->
            IntOffset(space.width - size.width, (space.height - size.height) / 2)
        }
        val BottomStart = Alignment { size, space ->
            IntOffset(0, space.height - size.height)
        }
        val BottomCenter = Alignment { size, space ->
            IntOffset((space.width - size.width) / 2, space.height - size.height)
        }
        val BottomEnd = Alignment { size, space ->
            IntOffset(space.width - size.width, space.height - size.height)
        }
    }
}

// ==================
// MARK: Horizontal / Vertical Alignment
// ==================

fun interface HorizontalAlignment {
    fun align(width: Int, space: Int): Int

    companion object {
        val Start = HorizontalAlignment { _, _ -> 0 }
        val CenterHorizontally = HorizontalAlignment { width, space -> (space - width) / 2 }
        val End = HorizontalAlignment { width, space -> space - width }
    }
}

fun interface VerticalAlignment {
    fun align(height: Int, space: Int): Int

    companion object {
        val Top = VerticalAlignment { _, _ -> 0 }
        val CenterVertically = VerticalAlignment { height, space -> (space - height) / 2 }
        val Bottom = VerticalAlignment { height, space -> space - height }
    }
}
