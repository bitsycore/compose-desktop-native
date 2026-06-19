package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.MeasurePolicy
import androidx.compose.ui.node.NodeApplier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

// ==================
// MARK: Row
// ==================

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = RowMeasurePolicy(horizontalArrangement, verticalAlignment)
            }
        },
        content = content
    )
}

private class RowMeasurePolicy(
    private val arrangement: Arrangement.Horizontal,
    private val alignment: Alignment.Vertical
) : MeasurePolicy {
    override fun measure(node: LayoutNode, constraints: Constraints): IntSize {
        val pl = node.paddingLeft; val pt = node.paddingTop
        val pr = node.paddingRight; val pb = node.paddingBottom

        val availW = if (constraints.maxWidth == Constraints.Infinity) Constraints.Infinity
                     else (constraints.maxWidth - pl - pr).coerceAtLeast(0)
        val availH = if (constraints.maxHeight == Constraints.Infinity) Constraints.Infinity
                     else (constraints.maxHeight - pt - pb).coerceAtLeast(0)

        val childConstraints = Constraints(
            minWidth = 0, maxWidth = availW,
            minHeight = 0, maxHeight = availH
        )

        val sizes = mutableListOf<Int>()
        var totalW = 0; var maxH = 0
        val gap = arrangement.spacing
        val gapTotal = if (node.children.size > 1) gap * (node.children.size - 1) else 0

        for (child in node.children) {
            val used = totalW + (if (sizes.isNotEmpty()) gap else 0)
            val remaining = if (availW == Constraints.Infinity) Constraints.Infinity
                            else (availW - used).coerceAtLeast(0)
            val cc = childConstraints.copy(maxWidth = remaining)
            val s = child.measure(cc)
            sizes.add(s.width)
            totalW += s.width
            maxH = max(maxH, s.height)
        }

        val w = (totalW + gapTotal + pl + pr).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (maxH + pt + pb).coerceIn(constraints.minHeight, constraints.maxHeight)
        val innerH = h - pt - pb

        val positions = IntArray(sizes.size)
        arrangement.arrange(w - pl - pr, sizes, positions)

        node.children.forEachIndexed { i, child ->
            val yOff = alignment.align(child.height, innerH)
            child.place(positions[i] + pl, yOff + pt)
        }

        return IntSize(w, h)
    }
}
