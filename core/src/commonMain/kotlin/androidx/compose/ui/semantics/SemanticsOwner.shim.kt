package androidx.compose.ui.semantics

import androidx.compose.ui.node.LayoutNode

// Phase 9 stub — upstream SemanticsOwner. Marker + a no-op change notification
// (desktop a11y layer isn't wired). Vendored Owner declares `val semanticsOwner`.
class SemanticsOwner {
	fun notifySemanticsChange(node: LayoutNode, previousConfig: SemanticsConfiguration?) {}
}
