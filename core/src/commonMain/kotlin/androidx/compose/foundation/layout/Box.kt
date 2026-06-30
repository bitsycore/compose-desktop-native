package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutNode
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
    override fun MeasureScope.measure(
        inMeasurables: List<Measurable>,
        inConstraints: Constraints,
    ): MeasureResult {
        // Padding flows through the LayoutModifierNode chain (vendored
        // upstream `Padding.kt`) ahead of Box's measure — the constraints
        // we receive here are already reduced by any surrounding padding.
        val childMinW = if (propagateMinConstraints) inConstraints.minWidth else 0
        val childMinH = if (propagateMinConstraints) inConstraints.minHeight else 0
        val innerConstraints = Constraints(
            minWidth = childMinW,
            maxWidth = inConstraints.maxWidth,
            minHeight = childMinH,
            maxHeight = inConstraints.maxHeight,
        )

        var maxW = 0; var maxH = 0
        val placeables = inMeasurables.map { measurable ->
            measurable.measure(innerConstraints).also {
                maxW = max(maxW, it.width)
                maxH = max(maxH, it.height)
            }
        }

        val w = maxW.coerceIn(inConstraints.minWidth, inConstraints.maxWidth)
        val h = maxH.coerceIn(inConstraints.minHeight, inConstraints.maxHeight)
        val innerSpace = IntSize(w, h)

        return layout(w, h) {
            placeables.forEach { placeable ->
                val pos = alignment.align(
                    IntSize(placeable.width, placeable.height),
                    innerSpace,
                    LayoutDirection.Ltr,
                )
                placeable.placeAt(pos.x, pos.y)
            }
        }
    }
}
