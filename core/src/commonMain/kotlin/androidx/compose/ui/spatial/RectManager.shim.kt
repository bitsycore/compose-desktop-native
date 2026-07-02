package androidx.compose.ui.spatial

import androidx.compose.ui.node.LayoutNode

// Phase 9 stub — upstream RectManager is a spatial index of node bounding rects.
// No-op so vendored LayoutNode / Owner.rectManager resolve.
const val NotFound: Int = -1

class RectManager {
	internal fun recalculateRectIfDirty(node: LayoutNode) {}
	internal fun getOffsetFromRectListFor(index: Int): Long = 0L
}
