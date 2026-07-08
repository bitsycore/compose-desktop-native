package com.compose.sdl.node

import androidx.compose.runtime.AbstractApplier
import androidx.compose.ui.node.LayoutNode

// ==================
// MARK: NodeApplier
// ==================

/* Phase 9 B4 — composition applier over the vendored upstream LayoutNode.
   internal because LayoutNode is internal to :core; the :window layer drives
   composition through the public ComposeRootHost facade (which upcasts this to
   Applier<*>). */
internal class NodeApplier(root: LayoutNode) : AbstractApplier<LayoutNode>(root) {

	override fun insertTopDown(index: Int, instance: LayoutNode) {
		// Ignored. Insert is performed in [insertBottomUp] to build the tree bottom-up so a
		// LayoutNode is only attached to its parent (and thus to the Owner) AFTER its own
		// `modifier` update block has run. That ordering is what lets LayoutNode stage the
		// modifier as `pendingModifier` and apply it during attach() via the
		// `applyingModifierOnAttach` fast-path — which creates the whole node chain, syncs
		// aggregateChildKindSet, updates NodeChain.head, and only then runs attach lifecycles
		// head->tail. Attaching top-down instead routes every fresh modifier through the
		// incremental Differ path, where a node's onAttach can run while NodeChain.head and the
		// aggregate kind sets are still stale. Modifier.Nodes that traverse ancestors in
		// onAttach then fail: e.g. StyleInnerNode.onAttach does
		// `findNearestAncestor(OuterNodeKey) as StyleOuterNode` and the stale-head short-circuit
		// in visitAncestors makes it return null. Matches upstream UiApplier / DefaultUiApplier.
	}

	override fun insertBottomUp(index: Int, instance: LayoutNode) {
		current.insertAt(index, instance)
	}

	override fun remove(index: Int, count: Int) {
		current.removeAt(index, count)
	}

	override fun move(from: Int, to: Int, count: Int) {
		current.move(from, to, count)
	}

	override fun onClear() {
		root.removeAll()
	}
}
