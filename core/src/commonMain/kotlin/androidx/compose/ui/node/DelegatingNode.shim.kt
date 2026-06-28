package androidx.compose.ui.node

import androidx.compose.ui.Modifier

// ==================
// MARK: DelegatingNode shim
// ==================

/**
 * Project shim for upstream `androidx.compose.ui.node.DelegatingNode`.
 *
 * Upstream signature: `abstract class DelegatingNode : Modifier.Node()`.
 * Extending Modifier.Node here keeps the `it is DelegatingNode` checks
 * in vendored DelegatableNode well-typed (Kotlin K2 would otherwise
 * emit `Check for instance is always 'false'`).
 *
 * No instances are constructed in Phase 1 so the lifecycle is dormant;
 * `delegate` is null and `forEachImmediateDelegate` is a no-op.
 */
abstract class DelegatingNode : Modifier.Node() {

	val delegate: Modifier.Node? = null

	fun forEachImmediateDelegate(@Suppress("UNUSED_PARAMETER") inBlock: (Modifier.Node) -> Unit) {
		// no-op
	}
}
