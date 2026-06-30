package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.MeasurePolicy
import com.compose.desktop.native.node.NodeApplier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
        // Padding flows through the LayoutModifierNode chain (vendored
        // upstream `Padding.kt`) ahead of Box's measure — the constraints
        // we receive here are already reduced by any surrounding padding.
        val childMinW = if (propagateMinConstraints) constraints.minWidth else 0
        val childMinH = if (propagateMinConstraints) constraints.minHeight else 0
        val innerConstraints = Constraints(
            minWidth = childMinW,
            maxWidth = constraints.maxWidth,
            minHeight = childMinH,
            maxHeight = constraints.maxHeight,
        )

        var maxW = 0; var maxH = 0
        for (child in node.children) {
            val s = child.measure(innerConstraints)
            maxW = max(maxW, s.width)
            maxH = max(maxH, s.height)
        }

        val w = maxW.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = maxH.coerceIn(constraints.minHeight, constraints.maxHeight)
        val innerSpace = IntSize(w, h)

        for (child in node.children) {
            val childSize = IntSize(child.width, child.height)
            val pos = alignment.align(childSize, innerSpace, LayoutDirection.Ltr)
            child.place(pos.x, pos.y)
        }

        return IntSize(w, h)
    }
}
