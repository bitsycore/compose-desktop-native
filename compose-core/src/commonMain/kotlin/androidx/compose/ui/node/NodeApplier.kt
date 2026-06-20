package androidx.compose.ui.node

import androidx.compose.runtime.AbstractApplier

// ==================
// MARK: NodeApplier
// ==================

class NodeApplier(root: LayoutNode) : AbstractApplier<LayoutNode>(root) {

    override fun insertTopDown(index: Int, instance: LayoutNode) {
        current.insertAt(index, instance)
    }

    override fun insertBottomUp(index: Int, instance: LayoutNode) {
        // Top-down insertion only
    }

    override fun remove(index: Int, count: Int) {
        current.removeAt(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.move(from, to, count)
    }

    override fun onClear() {
        root.children.clear()
    }
}
