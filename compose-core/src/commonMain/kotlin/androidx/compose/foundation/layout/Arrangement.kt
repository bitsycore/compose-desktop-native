package androidx.compose.foundation.layout

import androidx.compose.ui.unit.Dp
import kotlin.math.max

// ==================
// MARK: Arrangement
// ==================

object Arrangement {

    interface Horizontal {
        fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray)
        /* Inter-child spacing that the arrangement injects regardless of totalSize.
           Row/Column read this so the parent size accounts for visible gaps. */
        val spacing: Int get() = 0
    }

    interface Vertical {
        fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray)
        val spacing: Int get() = 0
    }

    /** Can be used as both Horizontal and Vertical. */
    interface HorizontalOrVertical : Horizontal, Vertical {
        override val spacing: Int get() = 0
    }

    // ============
    //  Horizontal-only

    val Start = object : Horizontal {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            var current = 0
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size }
        }
    }

    val End = object : Horizontal {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            var current = totalSize - sizes.sum()
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size }
        }
    }

    // ============
    //  Vertical-only

    val Top = object : Vertical {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            var current = 0
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size }
        }
    }

    val Bottom = object : Vertical {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            var current = totalSize - sizes.sum()
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size }
        }
    }

    // ============
    //  HorizontalOrVertical

    val Center = object : HorizontalOrVertical {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            var current = (totalSize - sizes.sum()) / 2
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size }
        }
    }

    val SpaceBetween = object : HorizontalOrVertical {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            if (sizes.size <= 1) {
                sizes.forEachIndexed { i, _ -> outPositions[i] = 0 }
                return
            }
            val gap = (totalSize - sizes.sum()) / (sizes.size - 1)
            var current = 0
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size + gap }
        }
    }

    val SpaceEvenly = object : HorizontalOrVertical {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            val gap = (totalSize - sizes.sum()) / (sizes.size + 1)
            var current = gap
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size + gap }
        }
    }

    val SpaceAround = object : HorizontalOrVertical {
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            val gap = (totalSize - sizes.sum()) / max(1, sizes.size)
            var current = gap / 2
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size + gap }
        }
    }

    fun spacedBy(space: Dp): HorizontalOrVertical = object : HorizontalOrVertical {
        private val gap = space.value.toInt()
        override val spacing: Int get() = gap
        override fun arrange(totalSize: Int, sizes: List<Int>, outPositions: IntArray) {
            var current = 0
            sizes.forEachIndexed { i, size -> outPositions[i] = current; current += size + gap }
        }
    }
}
