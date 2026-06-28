package androidx.compose.ui.node

import androidx.compose.ui.Modifier

// ==================
// MARK: NodeKind + Nodes shim
// ==================

/**
 * Project shim for upstream `androidx.compose.ui.node.NodeKind` value class.
 *
 * Type parameter `<T>` is left unbounded (defaults to `Any?`) to match
 * upstream — the engine's traversal helpers (`visitAncestors<T>(kind)` etc.)
 * pass unbounded `T`, so a tighter bound here breaks those call sites.
 *
 * Real upstream NodeKind drives Modifier.Node aggregation/traversal via
 * `kindSet` bit masks. Phase 1 has no Modifier.Node instances, so every
 * `kindSet` is 0 and `isKind(kind)` always returns false — every
 * `visitX(...)` traversal in vendored DelegatableNode short-circuits.
 *
 * Delete once the real NodeKind.kt is vendored (Phase 4).
 */
@kotlin.jvm.JvmInline
value class NodeKind<T>(val mask: Int)

/**
 * Bit-mask constants for the kinds DelegatableNode references in its
 * `visitX(Nodes.Y)` helpers. Real `Nodes` object upstream has ~14
 * constants — only the ones DelegatableNode passes through are shimmed
 * here, plus a handful of foreseeable downstream needs.
 */
internal object Nodes {
	val Any = NodeKind<Modifier.Node>(0b0000_0000_0000_0001)
	val Layout = NodeKind<LayoutModifierNode>(0b0000_0000_0000_0010)
	val Draw = NodeKind<DrawModifierNode>(0b0000_0000_0000_0100)
	val Locals = NodeKind<androidx.compose.ui.modifier.ModifierLocalModifierNode>(0b0000_0000_0000_1000)
	val BeyondBoundsLayout = NodeKind<androidx.compose.ui.layout.BeyondBoundsLayoutProviderModifierNode>(0b0000_0000_0001_0000)
	val Semantics = NodeKind<Modifier.Node>(0b0000_0000_0010_0000)
	val PointerInput = NodeKind<Modifier.Node>(0b0000_0000_0100_0000)
	val FocusTarget = NodeKind<Modifier.Node>(0b0000_0000_1000_0000)
	val GlobalPositionAware = NodeKind<Modifier.Node>(0b0000_0001_0000_0000)
	val LayoutAware = NodeKind<Modifier.Node>(0b0000_0010_0000_0000)
	val ParentData = NodeKind<Modifier.Node>(0b0000_0100_0000_0000)
	val Traversable = NodeKind<TraversableNode>(0b0000_1000_0000_0000)
}

internal infix fun NodeKind<*>.or(inOther: NodeKind<*>): Int = mask or inOther.mask
internal infix fun NodeKind<*>.or(inMask: Int): Int = mask or inMask
internal infix fun Int.or(inKind: NodeKind<*>): Int = this or inKind.mask

/**
 * `NodeKind<*>.includeSelfInTraversal` matches upstream's
 * NodeKind.kt:65 — checks for OnRemeasured / OnPlaced kind bits. Our
 * Nodes shim has no OnRemeasured / OnPlaced entries (they aren't
 * referenced by DelegatableNode itself), so this always returns false in
 * Phase 1.
 */
internal val NodeKind<*>.includeSelfInTraversal: Boolean
	get() = false

// ==================
// MARK: calculateNodeKindSetFrom helpers
// ==================

/**
 * Walk a [Modifier.Node]'s interface implementations and OR together the
 * matching [Nodes] bits. Subset of upstream NodeKind.kt:216 — only checks
 * the interfaces we currently track in [Nodes]; new entries should be
 * added here too.
 */
internal fun calculateNodeKindSetFrom(node: Modifier.Node): Int {
	var vMask = Nodes.Any.mask
	if (node is LayoutModifierNode) vMask = vMask or Nodes.Layout
	if (node is DrawModifierNode) vMask = vMask or Nodes.Draw
	if (node is androidx.compose.ui.modifier.ModifierLocalModifierNode) vMask = vMask or Nodes.Locals
	if (node is androidx.compose.ui.layout.BeyondBoundsLayoutProviderModifierNode) vMask = vMask or Nodes.BeyondBoundsLayout
	if (node is TraversableNode) vMask = vMask or Nodes.Traversable
	return vMask
}

/**
 * Upstream NodeKind.kt:430 — kind aggregation for a delegating node walks
 * its delegate subtree. With DelegatingNode still a shim that exposes
 * neither `selfKindSet` nor a real delegate chain, this collapses to
 * [calculateNodeKindSetFrom] of the node itself; when the real
 * DelegatingNode lands, restore the upstream walk.
 */
internal fun calculateNodeKindSetFromIncludingDelegates(node: Modifier.Node): Int =
	calculateNodeKindSetFrom(node)

