package androidx.compose.ui.platform

import androidx.compose.runtime.AbstractApplier
import androidx.compose.ui.node.LayoutNode

// Native actual for vendored commonMain Subcomposition.kt (upstream's SubcomposeLayout
// composition factory, keyed on the upstream LayoutNode). The project drives its own
// composition through com.compose.desktop.native.node.NodeApplier over LayoutNode,
// so this upstream path is unused at runtime — a no-op AbstractApplier<LayoutNode>
// satisfies the expect. Retires when SubcomposeLayout is genuinely wired (Phase 9+).
internal actual fun createApplier(container: LayoutNode): AbstractApplier<LayoutNode> =
	object : AbstractApplier<LayoutNode>(container) {
		override fun onClear() {}
		override fun insertTopDown(index: Int, instance: LayoutNode) {}
		override fun insertBottomUp(index: Int, instance: LayoutNode) {}
		override fun remove(index: Int, count: Int) {}
		override fun move(from: Int, to: Int, count: Int) {}
	}
