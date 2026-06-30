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
    private val alignment: Alignment.Vertical,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        inMeasurables: List<Measurable>,
        inConstraints: Constraints,
    ): MeasureResult {
        val availW = inConstraints.maxWidth
        val availH = inConstraints.maxHeight

        val gap = arrangement.spacing
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
        val hasWeights = totalWeight > 0f && availW != Constraints.Infinity

        var maxH = 0
        var consumedW = 0
        val placeables = arrayOfNulls<Placeable>(n)

        // Pass 1: unweighted children at intrinsic size.
        for (i in 0 until n) {
            if (weights[i] > 0f && hasWeights) continue
            val remaining = if (availW == Constraints.Infinity) Constraints.Infinity
                            else (availW - consumedW - gapTotal).coerceAtLeast(0)
            val cc = Constraints(minWidth = 0, maxWidth = remaining, minHeight = 0, maxHeight = availH)
            val p = inMeasurables[i].measure(cc)
            placeables[i] = p
            consumedW += p.width
            maxH = max(maxH, p.height)
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
                val p = inMeasurables[i].measure(cc)
                placeables[i] = p
                maxH = max(maxH, p.height)
            }
        }

        val sizes = IntArray(n) { placeables[it]!!.width }
        val totalChildW = sizes.sum()
        val w = (totalChildW + gapTotal).coerceIn(inConstraints.minWidth, inConstraints.maxWidth)
        val h = maxH.coerceIn(inConstraints.minHeight, inConstraints.maxHeight)

        val positions = IntArray(n)
        arrangement.arrange(w, sizes.toList(), positions)

        return layout(w, h) {
            for (i in 0 until n) {
                val placeable = placeables[i]!!
                val yOff = alignment.align(placeable.height, h)
                placeable.placeAt(positions[i], yOff)
            }
        }
    }
}
