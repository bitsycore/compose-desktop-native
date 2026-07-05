package com.compose.desktop.native.node

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
		current.insertAt(index, instance)
	}

	override fun insertBottomUp(index: Int, instance: LayoutNode) {
		// Top-down insertion only.
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
