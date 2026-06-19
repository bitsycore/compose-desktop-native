package compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import compose.ui.*
import compose.ui.node.LayoutNode
import compose.ui.node.NodeApplier
import kotlin.math.max

// ==================
// MARK: Box
// ==================

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit = {}
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(contentAlignment) {
                this.measurePolicy = BoxMeasurePolicy(it)
            }
        },
        content = content
    )
}

private class BoxMeasurePolicy(private val alignment: Alignment) : compose.ui.node.MeasurePolicy {
    override fun measure(node: LayoutNode, constraints: Constraints): IntSize {
        val pl = node.paddingLeft; val pt = node.paddingTop
        val pr = node.paddingRight; val pb = node.paddingBottom

        val innerConstraints = Constraints(
            minWidth = (constraints.minWidth - pl - pr).coerceAtLeast(0),
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) Constraints.Infinity
                       else (constraints.maxWidth - pl - pr).coerceAtLeast(0),
            minHeight = (constraints.minHeight - pt - pb).coerceAtLeast(0),
            maxHeight = if (constraints.maxHeight == Constraints.Infinity) Constraints.Infinity
                        else (constraints.maxHeight - pt - pb).coerceAtLeast(0),
        )

        var maxW = 0; var maxH = 0
        for (child in node.children) {
            val s = child.measure(innerConstraints)
            maxW = max(maxW, s.width)
            maxH = max(maxH, s.height)
        }

        val w = (maxW + pl + pr).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (maxH + pt + pb).coerceIn(constraints.minHeight, constraints.maxHeight)
        val innerSpace = IntSize(w - pl - pr, h - pt - pb)

        for (child in node.children) {
            val childSize = IntSize(child.width, child.height)
            val pos = alignment.align(childSize, innerSpace)
            child.place(pos.x + pl, pos.y + pt)
        }

        return IntSize(w, h)
    }
}
