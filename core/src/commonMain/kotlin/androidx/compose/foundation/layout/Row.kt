package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Alignment
import com.compose.desktop.native.element.LayoutWeightModifier
import androidx.compose.ui.Modifier
import com.compose.desktop.native.node.LayoutNode
import com.compose.desktop.native.node.MeasurePolicy
import com.compose.desktop.native.node.NodeApplier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

// ==================
// MARK: Row
// ==================

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = RowMeasurePolicy(horizontalArrangement, verticalAlignment)
            }
        },
        content = { RowScope.content() }
    )
}

/* Two-pass main-axis measurement. First pass: measure every child WITHOUT a
   weight at its intrinsic width (capped at remaining space). Second pass:
   divide leftover width among weighted children proportional to their
   weight; fill=true children get exactly their slice as a fixed width,
   fill=false children get 0..slice (their preferred). When the parent has
   no bounded width the weighted children fall back to their intrinsic
   size (weight is meaningless without a finite pool to split). */
private class RowMeasurePolicy(
    private val arrangement: Arrangement.Horizontal,
    private val alignment: Alignment.Vertical
) : MeasurePolicy {
    override fun measure(node: LayoutNode, constraints: Constraints): IntSize {
        // Padding flows through the LayoutModifierNode chain (vendored
        // upstream `Padding.kt`) ahead of Row's measure; constraints here
        // are already reduced.
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
        val hasWeights = totalWeight > 0f && availW != Constraints.Infinity

        var maxH = 0
        var consumedW = 0
        // Pass 1: unweighted children at intrinsic size.
        for (i in 0 until n) {
            if (weights[i] > 0f && hasWeights) continue
            val remaining = if (availW == Constraints.Infinity) Constraints.Infinity
                            else (availW - consumedW - gapTotal).coerceAtLeast(0)
            val cc = Constraints(minWidth = 0, maxWidth = remaining, minHeight = 0, maxHeight = availH)
            val s = node.children[i].measure(cc)
            sizes[i] = s.width
            consumedW += s.width
            maxH = max(maxH, s.height)
        }
        // Pass 2: weighted children share the leftover width.
        if (hasWeights) {
            val leftover = (availW - consumedW - gapTotal).coerceAtLeast(0)
            // Distribute integer pixels so the sum exactly equals `leftover`
            // (no rounding drift). Each weighted child gets floor(share)
            // and the largest remainders pick up the +1 leftovers.
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
                // Distribute the drift by largest fractional remainder.
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
                    minWidth = slice[i], maxWidth = slice[i],
                    minHeight = 0, maxHeight = availH,
                ) else Constraints(
                    minWidth = 0, maxWidth = slice[i],
                    minHeight = 0, maxHeight = availH,
                )
                val s = node.children[i].measure(cc)
                sizes[i] = s.width
                maxH = max(maxH, s.height)
            }
        }

        val totalChildW = sizes.sum()
        val w = (totalChildW + gapTotal).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = maxH.coerceIn(constraints.minHeight, constraints.maxHeight)

        val positions = IntArray(n)
        arrangement.arrange(w, sizes.toList(), positions)

        node.children.forEachIndexed { i, child ->
            val yOff = alignment.align(child.height, h)
            child.place(positions[i], yOff)
        }

        return IntSize(w, h)
    }

    private fun weightOf(inNode: LayoutNode): Float = inNode.cachedLayoutWeight?.weight ?: 0f
    private fun fillOf(inNode: LayoutNode): Boolean = inNode.cachedLayoutWeight?.fill ?: true
}
