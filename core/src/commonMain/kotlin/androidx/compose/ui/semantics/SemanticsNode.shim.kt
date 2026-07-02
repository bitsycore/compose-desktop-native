package androidx.compose.ui.semantics

import androidx.compose.ui.node.LayoutNode

// Phase 9 stub — semantics tree node; a11y runtime not wired.
internal class SemanticsNode(
	@Suppress("unused") val layoutNode: LayoutNode,
	@Suppress("unused") val mergingEnabled: Boolean,
) {
	fun isImportantForAccessibility(): Boolean = true
}

class SemanticsActions
