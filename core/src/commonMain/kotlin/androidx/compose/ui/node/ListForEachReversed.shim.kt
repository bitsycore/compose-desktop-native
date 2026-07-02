package androidx.compose.ui.node

// ==================
// MARK: List.forEachReversed extension shim
// ==================

/**
 * Project shim. Vendored DelegatableNode calls `.forEachReversed { ... }`
 * on `ProjectLayoutNode.getChildren(zOrder)` which upstream returns
 * `MutableVector<ProjectLayoutNode>` — that runtime collection has a built-in
 * `forEachReversed`. We keep `_children` / `zSortedChildren` as
 * `MutableList<ProjectLayoutNode>` (matches our existing project surface) and
 * provide an equivalent extension here.
 *
 * `internal` so it doesn't escape the engine package. Delete when the
 * project ProjectLayoutNode is replaced by upstream's in Phase 4.
 */
internal inline fun <T> List<T>.forEachReversed(inBlock: (T) -> Unit) {
	for (i in indices.reversed()) inBlock(this[i])
}
