package androidx.compose.ui.layout

// ==================
// MARK: LayoutCoordinates (stub)
// ==================

/* Minimal stub of upstream's LayoutCoordinates interface (281 lines in
   upstream, depends on Matrix + NodeCoordinator engine glue we don't have).
   Vendored PointerEvent.kt + PointerInputChange use this only as a nullable
   `internal var` field, and read `size` for hit-testing in the upstream
   pointer pipeline — our project's hit-testing happens in ComposeWindow's
   event loop, so the field stays null and only the size getter is used.

   When/if we port the Modifier.Node infrastructure, replace this stub with
   the vendored interface. */
interface LayoutCoordinates {
	val size: androidx.compose.ui.unit.IntSize
		get() = androidx.compose.ui.unit.IntSize.Zero

	/**
	 * Phase 1 node-engine bring-up: vendored DelegatableNode reads
	 * `coordinates.isAttached` at line 412. Default `true` matches the
	 * "node is attached" state we report from ProjectLayoutNode.
	 */
	val isAttached: Boolean
		get() = true

	/**
	 * Phase 4b: vendored Ruler.kt calls
	 * `targetCoordinates.localPositionOf(sourceCoordinates, offset)` in
	 * VerticalRuler / HorizontalRuler's `calculateCoordinate`. No Ruler
	 * instances are constructed in our pipeline, so this is never
	 * invoked at runtime; default returns the offset unchanged.
	 */
	fun localPositionOf(
		inSourceCoordinates: LayoutCoordinates,
		inRelativeToSource: androidx.compose.ui.geometry.Offset,
	): androidx.compose.ui.geometry.Offset = inRelativeToSource

	/**
	 * Position of this layout in the window's logical coordinate space.
	 * Upstream returns `Offset` (float pixels); we report
	 * `(absoluteX, absoluteY)` from the owning ProjectLayoutNode in logical
	 * points. Default `Offset.Zero` for the stub `coordinates` instance
	 * — every real ProjectLayoutNode overrides via [NodeLayoutCoordinates].
	 */
	fun positionInWindow(): androidx.compose.ui.geometry.Offset =
		androidx.compose.ui.geometry.Offset.Zero

	/** Same as [positionInWindow] for our flat single-window model. */
	fun positionInRoot(): androidx.compose.ui.geometry.Offset = positionInWindow()

	// ── Phase 9 surface expansion — upstream NodeCoordinator overrides these.
	//    Defaults keep existing project impls compiling.
	val providedAlignmentLines: Set<AlignmentLine>
		get() = emptySet()
	val parentLayoutCoordinates: LayoutCoordinates?
		get() = null
	val parentCoordinates: LayoutCoordinates?
		get() = null
	val introducesMotionFrameOfReference: Boolean
		get() = false

	fun localToRoot(relativeToLocal: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset = relativeToLocal
	fun localToWindow(relativeToLocal: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset = relativeToLocal
	fun localToScreen(relativeToLocal: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset = relativeToLocal
	fun screenToLocal(relativeToScreen: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset = relativeToScreen
	fun windowToLocal(relativeToWindow: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset = relativeToWindow

	fun localPositionOf(
		sourceCoordinates: LayoutCoordinates,
		relativeToSource: androidx.compose.ui.geometry.Offset,
		includeMotionFrameOfReference: Boolean,
	): androidx.compose.ui.geometry.Offset = localPositionOf(sourceCoordinates, relativeToSource)

	fun localBoundingBoxOf(
		sourceCoordinates: LayoutCoordinates,
		clipBounds: Boolean = true,
	): androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect(0f, 0f, 0f, 0f)

	fun transformFrom(sourceCoordinates: LayoutCoordinates, matrix: androidx.compose.ui.graphics.Matrix) {}
	fun transformToScreen(matrix: androidx.compose.ui.graphics.Matrix) {}
}

// Phase 9 extension stand-ins — upstream declares these top-level in LayoutCoordinates.kt.
fun LayoutCoordinates.findRootCoordinates(): LayoutCoordinates {
	var vCurrent: LayoutCoordinates = this
	var vParent = parentLayoutCoordinates
	while (vParent != null) { vCurrent = vParent; vParent = vParent.parentLayoutCoordinates }
	return vCurrent
}

fun LayoutCoordinates.positionOnScreen(): androidx.compose.ui.geometry.Offset =
	localToScreen(androidx.compose.ui.geometry.Offset.Zero)
