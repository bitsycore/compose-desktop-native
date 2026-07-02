package androidx.compose.ui.node

import androidx.compose.ui.Modifier
import com.compose.desktop.native.node.ProjectLayoutNode

// ==================
// MARK: NodeChain
// ==================

/**
 * Per-ProjectLayoutNode chain of [Modifier.Node] instances built from the
 * `ModifierNodeElement` entries in the layout's modifier chain.
 *
 * Upstream's NodeChain is doubly-linked and serves as both the storage
 * and the dispatch path for Modifier.Node lifecycle. Ours is a single-
 * pass builder: when [ProjectLayoutNode.modifier] is reassigned, [update]
 * walks the chain with `foldIn`, finds every [ModifierNodeElement],
 * calls its `create()` (or `update(node)` on an existing matched node),
 * and links the results via `parent` / `child`. `head` stays pinned to
 * a sentinel so vendored DelegatableNode's `nodes.head` / `nodes.tail`
 * traversals always have a non-null starting point; the real nodes live
 * between `head.child` and `tail`.
 *
 * Phase 4j: this chain now drives the **Modifier.Node lifecycle** —
 * newly `create()`d nodes get their coordinator assigned, then
 * `markAsAttached()` + `runAttachLifecycle()` (which fires `onAttach()`);
 * removed nodes get `runDetachLifecycle()` + `markAsDetached()`. The
 * render path still walks `Modifier.foldIn { is XxxModifier }` directly,
 * so today's Modifier.Node `onAttach`/`onDetach` overrides see a real
 * lifecycle but no draw / measure dispatch yet — that comes when the
 * renderer rewrite uses `DelegatableNode.visit*` traversal instead of
 * foldIn.
 */
internal class NodeChain(private val fOwner: ProjectLayoutNode) {

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
		val vCreatedNodes = ArrayList<Modifier.Node>(vNewElements.size)
		val vReplacedOld = ArrayList<Modifier.Node>(vOldNodes.size)

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
				vCreated.kindSet = calculateNodeKindSetFromIncludingDelegates(vCreated)
				fOldElementForNode[vCreated] = vElement
				vNewNodes.add(vCreated)
				vCreatedNodes.add(vCreated)
				if (vReused != null) vReplacedOld.add(vReused)
			}
		}

		// Tail-end old nodes that have no slot in the new chain are leaving.
		for (i in vNewNodes.size until vOldNodes.size) {
			vReplacedOld.add(vOldNodes[i])
		}

		// Detach lifecycle for removed nodes (in reverse to mirror upstream's tailToHead pass).
		for (i in vReplacedOld.indices.reversed()) {
			val vN = vReplacedOld[i]
			if (vN.isAttached) {
				vN.runDetachLifecycle()
				vN.markAsDetached()
			}
			fOldElementForNode.remove(vN)
			vN.parent = null
			vN.child = null
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

		// Attach lifecycle for newly created nodes — coordinator first
		// (markAsAttached() requires a non-null coordinator), then
		// markAsAttached, then runAttachLifecycle (which fires onAttach).
		// Two passes head-to-tail mirror upstream's "mark all, then run
		// lifecycles" so onAttach bodies see a fully-attached chain.
		val vCoordinator = fOwner.coordinator
		for (vN in vCreatedNodes) {
			vN.updateCoordinator(vCoordinator)
			vN.markAsAttached()
		}
		for (vN in vCreatedNodes) {
			vN.runAttachLifecycle()
		}
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
	 * Upstream NodeChain.kt:248 — per-modifier `LayoutModifierNodeCoordinator`
	 * threading. Our project keeps a single NodeCoordinator per ProjectLayoutNode
	 * (no per-modifier chain), so this is a no-op. Vendored DelegatingNode
	 * calls it after `delegate(...)` adds a layout-modifier delegate; the
	 * draw / measure path is then re-driven by the renderer, which currently
	 * still reads via foldIn — not via the coordinator chain.
	 *
	 * Becomes real when the renderer rewrite uses the upstream
	 * `LayoutModifierNodeCoordinator` chain instead of [ProjectLayoutNode.measure]
	 * with the flat foldIn pass.
	 */
	fun syncCoordinators() { /* no-op */ }

	/**
	 * Concrete Modifier.Node subclass used as the chain's permanent
	 * head. Never visible as a "real" modifier — DelegatableNode walks
	 * `head.child` to skip it.
	 */
	private class SentinelHead : Modifier.Node()
}
