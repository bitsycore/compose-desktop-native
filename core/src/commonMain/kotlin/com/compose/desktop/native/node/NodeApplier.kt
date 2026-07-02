package com.compose.desktop.native.node

import androidx.compose.runtime.AbstractApplier
import com.compose.desktop.native.node.ProjectLayoutNode

// ==================
// MARK: NodeApplier
// ==================

class NodeApplier(root: ProjectLayoutNode) : AbstractApplier<ProjectLayoutNode>(root) {

    override fun insertTopDown(index: Int, instance: ProjectLayoutNode) {
        current.insertAt(index, instance)
    }

    override fun insertBottomUp(index: Int, instance: ProjectLayoutNode) {
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
