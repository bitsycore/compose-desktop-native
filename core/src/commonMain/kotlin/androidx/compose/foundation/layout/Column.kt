package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Alignment
import com.compose.desktop.native.element.LayoutWeightModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.LayoutNode
import com.compose.desktop.native.node.NodeApplier
import androidx.compose.ui.unit.Constraints
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
        content = { ColumnScopeInstance.content() }
    )
}

/* See RowMeasurePolicy for the two-pass weighted-measurement scheme;
   Column is the same with the main axis being vertical. */
private class ColumnMeasurePolicy(
    private val arrangement: Arrangement.Vertical,
    private val alignment: Alignment.Horizontal,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        inMeasurables: List<Measurable>,
        inConstraints: Constraints,
    ): MeasureResult {
        val availW = inConstraints.maxWidth
        val availH = inConstraints.maxHeight

        val gap = arrangement.spacing.roundToPx()
        val n = inMeasurables.size
        val gapTotal = if (n > 1) gap * (n - 1) else 0

        val weights = FloatArray(n)
        val fills = BooleanArray(n)
        for (i in 0 until n) {
            val w = inMeasurables[i].parentData as? LayoutWeightModifier
            weights[i] = w?.weight ?: 0f
            fills[i] = w?.fill ?: true
        }
        val totalWeight = weights.sum()
        val hasWeights = totalWeight > 0f && availH != Constraints.Infinity

        var maxW = 0
        var consumedH = 0
        val placeables = arrayOfNulls<Placeable>(n)

        for (i in 0 until n) {
            if (weights[i] > 0f && hasWeights) continue
            val remaining = if (availH == Constraints.Infinity) Constraints.Infinity
                            else (availH - consumedH - gapTotal).coerceAtLeast(0)
            val cc = Constraints(minWidth = 0, maxWidth = availW, minHeight = 0, maxHeight = remaining)
            val p = inMeasurables[i].measure(cc)
            placeables[i] = p
            consumedH += p.height
            maxW = max(maxW, p.width)
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
                val p = inMeasurables[i].measure(cc)
                placeables[i] = p
                maxW = max(maxW, p.width)
            }
        }

        val sizes = IntArray(n) { placeables[it]!!.height }
        val totalChildH = sizes.sum()
        val w = maxW.coerceIn(inConstraints.minWidth, inConstraints.maxWidth)
        val h = (totalChildH + gapTotal).coerceIn(inConstraints.minHeight, inConstraints.maxHeight)

        val positions = IntArray(n)
        with(arrangement) { arrange(h, sizes, positions) }

        return layout(w, h) {
            for (i in 0 until n) {
                val placeable = placeables[i]!!
                val xOff = alignment.align(placeable.width, w, LayoutDirection.Ltr)
                placeable.placeAt(xOff, positions[i])
            }
        }
    }
}
