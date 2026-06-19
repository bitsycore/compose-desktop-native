package compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import compose.ui.*
import compose.ui.node.LayoutNode
import compose.ui.node.MeasurePolicy
import compose.ui.node.NodeApplier

// ==================
// MARK: Spacer
// ==================

@Composable
fun Spacer(modifier: Modifier = Modifier) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = SpacerMeasurePolicy
            }
        }
    )
}

private val SpacerMeasurePolicy = MeasurePolicy { node, constraints ->
    IntSize(
        constraints.minWidth.coerceAtMost(constraints.maxWidth),
        constraints.minHeight.coerceAtMost(constraints.maxHeight)
    )
}
