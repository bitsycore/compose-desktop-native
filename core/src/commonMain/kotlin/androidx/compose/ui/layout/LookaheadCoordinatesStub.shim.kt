package androidx.compose.ui.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.node.LookaheadDelegate
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.unit.IntSize

// Phase 9 stub — lookahead pipeline unvendored; NodeCoordinator reads `.coordinator`
// and LookaheadDelegate constructs one. Now that LayoutCoordinates is vendored
// (with abstract members), we no-op every abstract call.
internal class LookaheadLayoutCoordinates(
	val lookaheadDelegate: LookaheadDelegate,
) : LayoutCoordinates {
	val coordinator: NodeCoordinator get() = lookaheadDelegate.coordinator

	override val size: IntSize get() = IntSize.Zero
	override val providedAlignmentLines: Set<AlignmentLine> get() = emptySet()
	override val parentLayoutCoordinates: LayoutCoordinates? get() = null
	override val parentCoordinates: LayoutCoordinates? get() = null
	override val isAttached: Boolean get() = true
	override val introducesMotionFrameOfReference: Boolean get() = false

	override fun windowToLocal(relativeToWindow: Offset): Offset = relativeToWindow
	override fun localToWindow(relativeToLocal: Offset): Offset = relativeToLocal
	override fun screenToLocal(relativeToScreen: Offset): Offset = relativeToScreen
	override fun localToScreen(relativeToLocal: Offset): Offset = relativeToLocal
	override fun localToRoot(relativeToLocal: Offset): Offset = relativeToLocal
	override fun localPositionOf(
		sourceCoordinates: LayoutCoordinates,
		relativeToSource: Offset,
	): Offset = relativeToSource

	override fun localPositionOf(
		sourceCoordinates: LayoutCoordinates,
		relativeToSource: Offset,
		includeMotionFrameOfReference: Boolean,
	): Offset = relativeToSource

	override fun localBoundingBoxOf(sourceCoordinates: LayoutCoordinates, clipBounds: Boolean): Rect =
		Rect(0f, 0f, 0f, 0f)

	override fun transformFrom(sourceCoordinates: LayoutCoordinates, matrix: Matrix) {}
	override fun transformToScreen(matrix: Matrix) {}
	override fun get(alignmentLine: AlignmentLine): Int = AlignmentLine.Unspecified
}
