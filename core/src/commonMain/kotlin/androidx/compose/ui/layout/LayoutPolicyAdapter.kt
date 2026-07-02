package androidx.compose.ui.layout

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.node.MeasurePolicy as InternalMeasurePolicy

// ==================
// MARK: adaptToInternal
// ==================

/**
 * Bridge the public upstream `MeasurePolicy` onto the project's internal
 * measure policy (`androidx.compose.ui.node.MeasurePolicy` — a simpler
 * `(ProjectLayoutNode, Constraints) -> IntSize` shape the renderer's measure
 * pipeline reads).
 *
 * Wraps each of the ProjectLayoutNode's children as a [LayoutNodeMeasurable],
 * builds a [MeasureScopeImpl], runs the user's measure body, invokes
 * `placeChildren()` on the returned [MeasureResult], and returns the
 * resolved [IntSize]. Called by `ProjectLayoutNode.measurePolicy` setter.
 */
internal fun adaptToInternal(inPolicy: MeasurePolicy): InternalMeasurePolicy =
	InternalMeasurePolicy { vNode, vConstraints ->
		val vMeasurables: List<Measurable> = vNode.children.map { LayoutNodeMeasurable(it) }
		val vScope = MeasureScopeImpl()
		val vResult = inPolicy.run { vScope.measure(vMeasurables, vConstraints) }
		vResult.placeChildren()
		IntSize(vResult.width, vResult.height)
	}
