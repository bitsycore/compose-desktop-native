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
// MARK: Box
// ==================

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(contentAlignment to propagateMinConstraints) {
                this.measurePolicy = BoxMeasurePolicy(it.first, it.second)
            }
        },
        content = content
    )
}

private class BoxMeasurePolicy(
    private val alignment: Alignment,
    private val propagateMinConstraints: Boolean,
) : MeasurePolicy {
    override fun measure(node: LayoutNode, constraints: Constraints): IntSize {
        val pl = node.paddingLeft; val pt = node.paddingTop
        val pr = node.paddingRight; val pb = node.paddingBottom

        // Compose's Box measures children with the incoming MIN constraints
        // loosened to 0 (unless propagateMinConstraints is set), then aligns
        // each child within the resolved box size. Propagating the min would
        // force a child to fill the box and defeat contentAlignment — e.g. a
        // single text line couldn't be vertically centred inside a min-height
        // container. The box itself still honours its own incoming min via the
        // coerceIn on w/h below.
        val childMinW = if (propagateMinConstraints) (constraints.minWidth - pl - pr).coerceAtLeast(0) else 0
        val childMinH = if (propagateMinConstraints) (constraints.minHeight - pt - pb).coerceAtLeast(0) else 0

        val innerConstraints = Constraints(
            minWidth = childMinW,
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) Constraints.Infinity
                       else (constraints.maxWidth - pl - pr).coerceAtLeast(0),
            minHeight = childMinH,
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
