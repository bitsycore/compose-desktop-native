package androidx.compose.ui.node

import androidx.compose.ui.Modifier

// ==================
// MARK: NodeChain shim
// ==================

/**
 * Project shim for upstream `androidx.compose.ui.node.NodeChain`.
 *
 * Real NodeChain holds the doubly-linked list of Modifier.Node instances
 * attached to a LayoutNode. Vendored DelegatableNode dereferences
 * `nodes.head` and `nodes.tail` without null-check — they must be
 * non-null `Modifier.Node` instances. We pin them at a single sentinel
 * node whose `kindSet = 0` and `child/parent = null`, so every traversal
 * helper bails on the first iteration.
 *
 * Delete in Phase 4.
 */
internal class NodeChain {

	/**
	 * Sentinel non-null Modifier.Node used as both `head` and `tail` in
	 * Phase 1. Its `kindSet` defaults to 0 (set by the vendored Modifier.Node
	 * superclass), `child` and `parent` are null — DelegatableNode's
	 * `visitX(...)` helpers walk `head.child` or `tail.parent` and immediately
	 * stop on the null.
	 */
	private object SentinelNode : Modifier.Node()

	val head: Modifier.Node = SentinelNode
	val tail: Modifier.Node = SentinelNode
}
