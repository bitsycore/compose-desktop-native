package androidx.compose.ui.node

import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable

// ==================
// MARK: LookaheadCapablePlaceable shim
// ==================

/**
 * Phase 4e shim for upstream `androidx.compose.ui.node.LookaheadCapablePlaceable`
 * (defined inside upstream LookaheadDelegate.kt, ~700 lines — the base
 * class for NodeCoordinator + LookaheadDelegate).
 *
 * Vendored `MeasureScope.kt`'s default `layout()` impl has a single
 * `this@MeasureScope is LookaheadCapablePlaceable` smart-cast that
 * accesses `placementScope` on the result. To make that type check
 * reachable to Kotlin's K2 ("always-false" checker), this shim extends
 * Placeable() and MeasureScope so a `MeasureScope is LookaheadCapablePlaceable`
 * check has at least one valid type relationship.
 *
 * No instances exist in Phase 4 — our MeasureScopeImpl is not a
 * LookaheadCapablePlaceable, so the `is` check is always false at
 * runtime and we fall through to the SimplePlacementScope branch.
 *
 * Delete when the real LookaheadDelegate.kt is vendored (Phase 4f+).
 */
internal abstract class LookaheadCapablePlaceable : Placeable(), MeasureScope {
	/** The PlacementScope used when this placeable's layout is committed. */
	abstract val placementScope: Placeable.PlacementScope
}
