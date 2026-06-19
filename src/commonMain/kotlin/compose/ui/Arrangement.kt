package compose.ui

import kotlin.math.max

// ==================
// MARK: Arrangement
// ==================

object Arrangement {

    fun interface Horizontal {
        fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray)
    }

    fun interface Vertical {
        fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray)
    }

    // ============
    //  Horizontal arrangements

    val Start = Horizontal { _, sizes, out ->
        var current = 0
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size
        }
    }

    val End = Horizontal { totalSize, sizes, out ->
        val usedSpace = sizes.sum()
        var current = totalSize - usedSpace
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size
        }
    }

    val Center = Horizontal { totalSize, sizes, out ->
        val usedSpace = sizes.sum()
        var current = (totalSize - usedSpace) / 2
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size
        }
    }

    val SpaceBetween = Horizontal { totalSize, sizes, out ->
        if (sizes.size <= 1) {
            sizes.forEachIndexed { i, _ -> out[i] = 0 }
            return@Horizontal
        }
        val usedSpace = sizes.sum()
        val gap = (totalSize - usedSpace) / (sizes.size - 1)
        var current = 0
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size + gap
        }
    }

    val SpaceEvenly = Horizontal { totalSize, sizes, out ->
        val usedSpace = sizes.sum()
        val gap = (totalSize - usedSpace) / (sizes.size + 1)
        var current = gap
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size + gap
        }
    }

    val SpaceAround = Horizontal { totalSize, sizes, out ->
        val usedSpace = sizes.sum()
        val gap = (totalSize - usedSpace) / max(1, sizes.size)
        var current = gap / 2
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size + gap
        }
    }

    fun spacedBy(space: Dp): Horizontal = Horizontal { _, sizes, out ->
        val gap = space.value.toInt()
        var current = 0
        sizes.forEachIndexed { i, size ->
            out[i] = current
            current += size + gap
        }
    }

    // ============
    //  Vertical — reuse the same logic

    val Top = Vertical { totalSize, sizes, out -> Start.arrange(totalSize, sizes, out) }
    val Bottom = Vertical { totalSize, sizes, out -> End.arrange(totalSize, sizes, out) }
    val CenterVertically = Vertical { totalSize, sizes, out -> Center.arrange(totalSize, sizes, out) }
    val SpaceBetweenVertically = Vertical { totalSize, sizes, out -> SpaceBetween.arrange(totalSize, sizes, out) }
    val SpaceEvenlyVertically = Vertical { totalSize, sizes, out -> SpaceEvenly.arrange(totalSize, sizes, out) }

    fun spacedByVertical(space: Dp): Vertical = Vertical { totalSize, sizes, out ->
        spacedBy(space).arrange(totalSize, sizes, out)
    }
}
