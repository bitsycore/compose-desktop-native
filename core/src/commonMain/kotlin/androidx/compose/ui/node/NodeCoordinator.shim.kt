package androidx.compose.ui.node

import androidx.compose.ui.layout.LayoutCoordinates

// ==================
// MARK: NodeCoordinator shim
// ==================

/**
 * Project shim for upstream `androidx.compose.ui.node.NodeCoordinator`
 * (1796 lines in upstream — the central rendering pipeline).
 *
 * Vendored DelegatableNode reads four members only: `tail`, `wrapped`,
 * `layoutNode`, `coordinates`. Stubs to error throws because nothing
 * creates a NodeCoordinator in Phase 1 — `Modifier.Node.coordinator` is the
 * only ref and stays null (lifecycle dormant). The error getters never
 * fire at runtime.
 *
 * Delete in Phase 4.
 */
internal class NodeCoordinator {

	val tail: DelegatableNode? = null

	val wrapped: NodeCoordinator? = null

	val layoutNode: LayoutNode
		get() = error("NodeCoordinator.layoutNode accessed without an active layout pipeline (Phase 1 shim).")

	val coordinates: LayoutCoordinates
		get() = error("NodeCoordinator.coordinates accessed without an active layout pipeline (Phase 1 shim).")
}
