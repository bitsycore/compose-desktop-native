package compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import compose.ui.*
import compose.ui.node.LayoutNode
import compose.ui.node.NodeApplier
import kotlin.math.max

// ==================
// MARK: Row
// ==================

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
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
    private val alignment: VerticalAlignment
) : compose.ui.node.MeasurePolicy {
    override fun measure(node: LayoutNode, constraints: Constraints): IntSize {
        val pl = node.paddingLeft; val pt = node.paddingTop
        val pr = node.paddingRight; val pb = node.paddingBottom

        val availW = if (constraints.maxWidth == Constraints.Infinity) Constraints.Infinity
                     else (constraints.maxWidth - pl - pr).coerceAtLeast(0)
        val availH = if (constraints.maxHeight == Constraints.Infinity) Constraints.Infinity
                     else (constraints.maxHeight - pt - pb).coerceAtLeast(0)

        val childConstraints = Constraints(
            minWidth = 0,
            maxWidth = availW,
            minHeight = 0,
            maxHeight = availH
        )

        val sizes = mutableListOf<Int>()
        var totalW = 0; var maxH = 0

        for (child in node.children) {
            val remaining = if (availW == Constraints.Infinity) Constraints.Infinity
                            else (availW - totalW).coerceAtLeast(0)
            val cc = childConstraints.copy(maxWidth = remaining)
            val s = child.measure(cc)
            sizes.add(s.width)
            totalW += s.width
            maxH = max(maxH, s.height)
        }

        val w = (totalW + pl + pr).coerceIn(constraints.minWidth, constraints.maxWidth)
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
