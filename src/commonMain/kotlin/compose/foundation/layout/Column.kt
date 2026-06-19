package compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import compose.ui.*
import compose.ui.node.LayoutNode
import compose.ui.node.NodeApplier
import kotlin.math.max

// ==================
// MARK: Column
// ==================

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    content: @Composable () -> Unit
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = ColumnMeasurePolicy(verticalArrangement, horizontalAlignment)
            }
        },
        content = content
    )
}

private class ColumnMeasurePolicy(
    private val arrangement: Arrangement.Vertical,
    private val alignment: HorizontalAlignment
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
        var maxW = 0; var totalH = 0

        for (child in node.children) {
            val remaining = if (availH == Constraints.Infinity) Constraints.Infinity
                            else (availH - totalH).coerceAtLeast(0)
            val cc = childConstraints.copy(maxHeight = remaining)
            val s = child.measure(cc)
            sizes.add(s.height)
            maxW = max(maxW, s.width)
            totalH += s.height
        }

        val w = (maxW + pl + pr).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (totalH + pt + pb).coerceIn(constraints.minHeight, constraints.maxHeight)
        val innerW = w - pl - pr

        val positions = IntArray(sizes.size)
        arrangement.arrange(h - pt - pb, sizes, positions)

        node.children.forEachIndexed { i, child ->
            val xOff = alignment.align(child.width, innerW)
            child.place(xOff + pl, positions[i] + pt)
        }

        return IntSize(w, h)
    }
}
