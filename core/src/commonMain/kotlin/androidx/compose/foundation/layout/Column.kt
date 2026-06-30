package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Alignment
import com.compose.desktop.native.element.LayoutWeightModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.MeasurePolicy
import com.compose.desktop.native.node.NodeApplier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max

// ==================
// MARK: Column
// ==================

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = ColumnMeasurePolicy(verticalArrangement, horizontalAlignment)
            }
        },
        content = { ColumnScope.content() }
    )
}

/* See RowMeasurePolicy for the two-pass weighted-measurement scheme;
   Column is the same with the main axis being vertical. */
private class ColumnMeasurePolicy(
    private val arrangement: Arrangement.Vertical,
    private val alignment: Alignment.Horizontal
) : MeasurePolicy {
    override fun measure(node: LayoutNode, constraints: Constraints): IntSize {
        // Padding flows through the LayoutModifierNode chain (vendored
        // upstream `Padding.kt`) ahead of Column's measure; constraints
        // here are already reduced.
        val availW = constraints.maxWidth
        val availH = constraints.maxHeight

        val gap = arrangement.spacing
        val n = node.children.size
        val gapTotal = if (n > 1) gap * (n - 1) else 0

        val sizes = IntArray(n)
        val weights = FloatArray(n)
        val fills = BooleanArray(n)
        for (i in 0 until n) weights[i] = weightOf(node.children[i]).also { fills[i] = fillOf(node.children[i]) }
        val totalWeight = weights.sum()
        val hasWeights = totalWeight > 0f && availH != Constraints.Infinity

        var maxW = 0
        var consumedH = 0
        for (i in 0 until n) {
            if (weights[i] > 0f && hasWeights) continue
            val remaining = if (availH == Constraints.Infinity) Constraints.Infinity
                            else (availH - consumedH - gapTotal).coerceAtLeast(0)
            val cc = Constraints(minWidth = 0, maxWidth = availW, minHeight = 0, maxHeight = remaining)
            val s = node.children[i].measure(cc)
            sizes[i] = s.height
            consumedH += s.height
            maxW = max(maxW, s.width)
        }
        if (hasWeights) {
            val leftover = (availH - consumedH - gapTotal).coerceAtLeast(0)
            val raw = FloatArray(n)
            for (i in 0 until n) if (weights[i] > 0f) raw[i] = leftover * weights[i] / totalWeight
            val slice = IntArray(n)
            var assigned = 0
            for (i in 0 until n) if (weights[i] > 0f) {
                slice[i] = raw[i].toInt()
                assigned += slice[i]
            }
            var drift = leftover - assigned
            if (drift != 0) {
                val order = (0 until n).filter { weights[it] > 0f }
                    .sortedByDescending { raw[it] - raw[it].toInt() }
                for (i in order) {
                    if (drift == 0) break
                    slice[i] += 1
                    drift -= 1
                }
            }
            for (i in 0 until n) if (weights[i] > 0f) {
                val cc = if (fills[i]) Constraints(
                    minWidth = 0, maxWidth = availW,
                    minHeight = slice[i], maxHeight = slice[i],
                ) else Constraints(
                    minWidth = 0, maxWidth = availW,
                    minHeight = 0, maxHeight = slice[i],
                )
                val s = node.children[i].measure(cc)
                sizes[i] = s.height
                maxW = max(maxW, s.width)
            }
        }

        val totalChildH = sizes.sum()
        val w = maxW.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (totalChildH + gapTotal).coerceIn(constraints.minHeight, constraints.maxHeight)

        val positions = IntArray(n)
        arrangement.arrange(h, sizes.toList(), positions)

        node.children.forEachIndexed { i, child ->
            val xOff = alignment.align(child.width, w, LayoutDirection.Ltr)
            child.place(xOff, positions[i])
        }

        return IntSize(w, h)
    }

    private fun weightOf(inNode: LayoutNode): Float = inNode.cachedLayoutWeight?.weight ?: 0f
    private fun fillOf(inNode: LayoutNode): Boolean = inNode.cachedLayoutWeight?.fill ?: true
}
