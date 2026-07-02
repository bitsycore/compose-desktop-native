package androidx.compose.ui.layout

import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize

// Phase 9 stub — approach/lookahead pass is unvendored, so no project node implements
// this; it exists as a type for NodeKind `is` checks + LayoutModifierNodeCoordinator.
interface ApproachLayoutModifierNode : LayoutModifierNode {
	fun isMeasurementApproachInProgress(lookaheadSize: IntSize): Boolean

	fun Placeable.PlacementScope.isPlacementApproachInProgress(
		lookaheadCoordinates: LayoutCoordinates,
	): Boolean = false

	fun ApproachMeasureScope.approachMeasure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult

	fun ApproachIntrinsicMeasureScope.minApproachIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0
	fun ApproachIntrinsicMeasureScope.minApproachIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0
	fun ApproachIntrinsicMeasureScope.maxApproachIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0
	fun ApproachIntrinsicMeasureScope.maxApproachIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0
}
