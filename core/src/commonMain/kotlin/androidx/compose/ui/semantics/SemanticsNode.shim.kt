package androidx.compose.ui.semantics

import androidx.compose.ui.node.LayoutNode

// Phase 9 stubs — semantics tree node + config access; a11y runtime not wired.
internal class SemanticsNode(
	@Suppress("unused") val layoutNode: LayoutNode,
	@Suppress("unused") val mergingEnabled: Boolean,
)
internal fun SemanticsNode.isImportantForAccessibility(): Boolean = true

object SemanticsActions {
	val OnClick = SemanticsPropertyKey<Any?>("OnClick")
}

fun <T> SemanticsConfiguration.getOrNull(key: SemanticsPropertyKey<T>): T? = null
