package androidx.compose.ui.node

import androidx.compose.ui.Modifier
import com.compose.desktop.native.node.LayoutNode

// ==================
// MARK: NodeChain
// ==================

/**
 * Per-LayoutNode chain of [Modifier.Node] instances built from the
 * `ModifierNodeElement` entries in the layout's modifier chain.
 *
 * Upstream's NodeChain is doubly-linked and serves as both the storage
 * and the dispatch path for Modifier.Node lifecycle. Ours is a single-
 * pass builder: when [LayoutNode.modifier] is reassigned, [update]
 * walks the chain with `foldIn`, finds every [ModifierNodeElement],
 * calls its `create()` (or `update(node)` on an existing matched node),
 * and links the results via `parent` / `child`. `head` stays pinned to
 * a sentinel so vendored DelegatableNode's `nodes.head` / `nodes.tail`
 * traversals always have a non-null starting point; the real nodes live
 * between `head.child` and `tail`.
 *
 * Phase 4i: this chain is **dormant on the render path** — the
 * renderer still walks `LayoutNode.modifier.foldIn { is XxxModifier }`
 * directly. The chain exists so vendored DelegatableNode traversals
 * (`visitAncestors` / `visitSelfAndChildren` / etc.) can light up with
 * real instances when the renderer rewrite hooks in. Lifecycle hooks
 * (`onAttach` / `onDetach`) are NOT driven yet — Modifier.Node's
 * `isAttached` stays false until a NodeCoordinator-style owner attaches
 * them, which is a future step.
 */
@Suppress("UNUSED_PARAMETER")
internal class NodeChain(owner: LayoutNode) {

	private val sentinel: Modifier.Node = SentinelHead()

	/** Always the sentinel. Real first node is `head.child`. */
	val head: Modifier.Node get() = sentinel

	/** Last real node, or [sentinel] when the chain is empty. */
	var tail: Modifier.Node = sentinel
		private set

	/** Diagnostic — number of real Modifier.Node instances in the chain. */
	var size: Int = 0
		private set

	/**
	 * Rebuild the chain from [inModifier]. Reuses existing nodes when an
	 * incoming [ModifierNodeElement] reports the same `equals` AND the same
	 * concrete type as the previous slot's element (delegating to the
	 * upstream `update(node)` hook); otherwise the slot gets a fresh
	 * `create()`.
	 */
	fun update(inModifier: Modifier) {
		val vNewElements = mutableListOf<ModifierNodeElement<*>>()
		inModifier.foldIn(Unit) { _, el ->
			if (el is ModifierNodeElement<*>) vNewElements.add(el)
		}

		val vOldNodes = mutableListOf<Modifier.Node>().also { collectRealNodes(it) }
		val vNewNodes = ArrayList<Modifier.Node>(vNewElements.size)

		for ((i, vElement) in vNewElements.withIndex()) {
			val vReused = vOldNodes.getOrNull(i)
			val vPrev = fOldElementForNode[vReused]
			if (vReused != null && vPrev == vElement) {
				vNewNodes.add(vReused)
			} else if (vReused != null && vPrev != null && vPrev::class == vElement::class) {
				@Suppress("UNCHECKED_CAST")
				(vElement as ModifierNodeElement<Modifier.Node>).update(vReused)
				fOldElementForNode[vReused] = vElement
				vNewNodes.add(vReused)
			} else {
				val vCreated = vElement.create()
				fOldElementForNode[vCreated] = vElement
				vNewNodes.add(vCreated)
			}
		}

		// Drop surplus old nodes (no-op while lifecycle is dormant).
		for (i in vNewNodes.size until vOldNodes.size) {
			fOldElementForNode.remove(vOldNodes[i])
		}

		// Re-link parent / child + reset sentinel.
		sentinel.child = vNewNodes.firstOrNull()
		for (i in vNewNodes.indices) {
			val vPrevNode = if (i == 0) sentinel else vNewNodes[i - 1]
			val vNextNode = vNewNodes.getOrNull(i + 1)
			vNewNodes[i].parent = vPrevNode
			vNewNodes[i].child = vNextNode
		}
		tail = vNewNodes.lastOrNull() ?: sentinel
		size = vNewNodes.size
	}

	/**
	 * Maps each live Modifier.Node back to the [ModifierNodeElement] that
	 * produced it — used by [update] to decide reuse-vs-recreate on the
	 * next pass. Cleared when a node leaves the chain.
	 */
	private val fOldElementForNode = HashMap<Modifier.Node, ModifierNodeElement<*>>()

	private fun collectRealNodes(out: MutableList<Modifier.Node>) {
		var n = sentinel.child
		while (n != null) {
			out.add(n)
			n = n.child
		}
	}

	/**
	 * Concrete Modifier.Node subclass used as the chain's permanent
	 * head. Never visible as a "real" modifier — DelegatableNode walks
	 * `head.child` to skip it.
	 */
	private class SentinelHead : Modifier.Node()
}
