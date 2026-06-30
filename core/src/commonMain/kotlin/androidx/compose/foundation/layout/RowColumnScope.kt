package androidx.compose.foundation.layout

import com.compose.desktop.native.element.LayoutWeightModifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine

// ==================
// MARK: RowScope / ColumnScope
// ==================

/**
 * Upstream-shape `interface RowScope` — hosts the `Modifier.weight` /
 * `Modifier.align` / `Modifier.alignBy` / `Modifier.alignByBaseline`
 * extensions. The project's measure pipeline reads `weight` /
 * `fill` from the resolved `LayoutWeightModifier` on each child;
 * `align*` / `alignBy*` are accept-and-ignored (project's Row doesn't
 * support per-child alignment overrides yet).
 *
 * `RowScopeInstance` is the singleton impl passed as receiver into
 * Row's content lambda.
 */
interface RowScope {
	fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
	fun Modifier.align(alignment: Alignment.Vertical): Modifier = this
	fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier = this
	fun Modifier.alignByBaseline(): Modifier = this
	fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

internal object RowScopeInstance : RowScope {
	override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
		require(weight > 0f) { "Weight must be > 0 (got $weight)" }
		return this.then(LayoutWeightModifier(weight, fill))
	}
}

/** ColumnScope — same shape as RowScope but the main axis is vertical. */
interface ColumnScope {
	fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
	fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
	fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
	fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

internal object ColumnScopeInstance : ColumnScope {
	override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
		require(weight > 0f) { "Weight must be > 0 (got $weight)" }
		return this.then(LayoutWeightModifier(weight, fill))
	}
}
