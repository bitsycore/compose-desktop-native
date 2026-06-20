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
// MARK: Column
// ==================

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
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
    private val alignment: Alignment.Horizontal
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
        var maxW = 0; var totalH = 0
        val gap = arrangement.spacing
        val gapTotal = if (node.children.size > 1) gap * (node.children.size - 1) else 0

        for (child in node.children) {
            val used = totalH + (if (sizes.isNotEmpty()) gap else 0)
            val remaining = if (availH == Constraints.Infinity) Constraints.Infinity
                            else (availH - used).coerceAtLeast(0)
            val cc = childConstraints.copy(maxHeight = remaining)
            val s = child.measure(cc)
            sizes.add(s.height)
            maxW = max(maxW, s.width)
            totalH += s.height
        }

        val w = (maxW + pl + pr).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (totalH + gapTotal + pt + pb).coerceIn(constraints.minHeight, constraints.maxHeight)
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
