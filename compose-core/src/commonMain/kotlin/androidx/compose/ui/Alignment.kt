package androidx.compose.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

// ==================
// MARK: Alignment (2D)
// ==================

fun interface Alignment {
    fun align(size: IntSize, space: IntSize): IntOffset

    // ==================
    // MARK: Horizontal
    // ==================

    fun interface Horizontal {
        fun align(size: Int, space: Int): Int
    }

    // ==================
    // MARK: Vertical
    // ==================

    fun interface Vertical {
        fun align(size: Int, space: Int): Int
    }

    companion object {
        // 2D alignments
        val TopStart = Alignment { _, _ -> IntOffset(0, 0) }
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
            IntOffset(
                (space.width - size.width) / 2,
                (space.height - size.height) / 2
            )
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

        // Horizontal
        val Start: Horizontal = Horizontal { _, _ -> 0 }
        val End: Horizontal = Horizontal { size, space -> space - size }
        val CenterHorizontally: Horizontal = Horizontal { size, space -> (space - size) / 2 }

        // Vertical
        val Top: Vertical = Vertical { _, _ -> 0 }
        val Bottom: Vertical = Vertical { size, space -> space - size }
        val CenterVertically: Vertical = Vertical { size, space -> (space - size) / 2 }
    }
}
