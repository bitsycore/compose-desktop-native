package androidx.compose.ui.layout

import androidx.compose.ui.node.LayoutModifierNodeCoordinator
import androidx.compose.ui.unit.IntSize

// Phase 9 stub — LayoutModifierNodeCoordinator constructs this for approach nodes.
// The approach pass isn't exercised in our simplified pipeline, so lookaheadSize is 0.
internal class ApproachMeasureScopeImpl(
	@Suppress("unused") val coordinator: LayoutModifierNodeCoordinator,
	var approachNode: ApproachLayoutModifierNode,
) : MeasureScopeImpl(), ApproachMeasureScope {
	var approachMeasureRequired: Boolean = false
	override val lookaheadSize: IntSize = IntSize.Zero
}
