package androidx.compose.ui.node

// ==================
// MARK: Invalidation enum — extracted shim (non-official)
// ==================

/**
 * Upstream defines this enum at the bottom of `MeasureAndLayoutDelegate.kt`
 * (which we can't vendor yet — pulls the full layout state machine).
 * Vendored `DepthSortedSet.kt` references it from
 * `DepthSortedSetsForDifferentPasses.add(node, invalidation)`, so we pull
 * the enum out as a standalone file. Retires when MeasureAndLayoutDelegate
 * lands in the Phase 9 bundle.
 */
internal enum class Invalidation {
	LookaheadMeasurement,
	LookaheadPlacement,
	Measurement,
	Placement,
}
