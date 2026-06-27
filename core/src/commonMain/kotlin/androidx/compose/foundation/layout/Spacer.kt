package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Modifier
import com.compose.desktop.native.node.LayoutNode
import com.compose.desktop.native.node.MeasurePolicy
import com.compose.desktop.native.node.NodeApplier
import androidx.compose.ui.unit.IntSize

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

private val SpacerMeasurePolicy = MeasurePolicy { _, constraints ->
    IntSize(
        constraints.minWidth.coerceAtMost(constraints.maxWidth),
        constraints.minHeight.coerceAtMost(constraints.maxHeight)
    )
}
