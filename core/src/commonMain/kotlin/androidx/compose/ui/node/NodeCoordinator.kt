package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize

// ==================
// MARK: NodeCoordinator
// ==================

/**
 * Per-ProjectLayoutNode owner of the Modifier.Node chain.
 *
 * Upstream's NodeCoordinator (1796 lines) is the central rendering
 * pipeline — one coordinator per modifier in the chain, threaded
 * together via `wrapped`, with each coordinator implementing
 * `MeasureScope` and driving its slice of the layout / draw pass.
 *
 * Our version is a **single coordinator per ProjectLayoutNode**, owned by the
 * layout, exposing only the surface vendored `DelegatableNode` reads:
 * `tail`, `wrapped`, `layoutNode`, `coordinates`, `invalidateLayer()`.
 * The Modifier.Node chain lives in [ProjectLayoutNode.nodes]; this coordinator
 * answers `tail` from there and otherwise stays out of the layout +
 * draw hot path (those still run through our project's
 * `ProjectLayoutNode.measure` / `place` + the renderer's `Modifier.foldIn`).
 *
 * Becomes real (and drives lifecycle / draw) when the renderer rewrite
 * lights up. Until then this is scaffolding that satisfies the
 * vendored DelegatableNode contract with proper layoutNode +
 * coordinates references — no more `error("...")` stubs.
 */
internal class NodeCoordinator(val owningLayoutNode: ProjectLayoutNode) : androidx.compose.ui.layout.IntrinsicMeasureScope {

	override val density: Float get() = owningLayoutNode.density.density
	override val fontScale: Float get() = owningLayoutNode.density.fontScale
	override val layoutDirection: androidx.compose.ui.unit.LayoutDirection
		get() = owningLayoutNode.layoutDirection

	/** Upstream pipes min/max-intrinsic through this — receiver scope for
	 *  `MeasurePolicy.intrinsicX(...)` extensions. The project's layout
	 *  pass doesn't query intrinsics today, so these methods are reachable
	 *  from vendored consumers (`IntrinsicsPolicy`) but unused at runtime. */
	internal fun minIntrinsicWidth(
		measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
		height: Int,
	): Int {
		val policy = owningLayoutNode.measurePolicy
		return with(policy) { minIntrinsicWidth(measurables, height) }
	}

	internal fun minIntrinsicHeight(
		measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
		width: Int,
	): Int {
		val policy = owningLayoutNode.measurePolicy
		return with(policy) { minIntrinsicHeight(measurables, width) }
	}

	internal fun maxIntrinsicWidth(
		measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
		height: Int,
	): Int {
		val policy = owningLayoutNode.measurePolicy
		return with(policy) { maxIntrinsicWidth(measurables, height) }
	}

	internal fun maxIntrinsicHeight(
		measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
		width: Int,
	): Int {
		val policy = owningLayoutNode.measurePolicy
		return with(policy) { maxIntrinsicHeight(measurables, width) }
	}

	/**
	 * Last real `Modifier.Node` in [ProjectLayoutNode.nodes], or null when the
	 * chain only has the sentinel head. DelegatableNode walks
	 * `tail.parent` for ancestor traversal — null tail short-circuits.
	 */
	val tail: DelegatableNode?
		get() = owningLayoutNode.nodes.tail.takeUnless { it === owningLayoutNode.nodes.head }

	/**
	 * Upstream threads one coordinator per modifier; we collapse to one
	 * per ProjectLayoutNode, so wrapping is always null.
	 */
	val wrapped: NodeCoordinator? = null

	val layoutNode: ProjectLayoutNode get() = owningLayoutNode

	val coordinates: LayoutCoordinates = LayoutNodeCoordinates(owningLayoutNode)

	/**
	 * Phase 2 surface: vendored DrawModifierNode.invalidateDraw() calls
	 * `requireCoordinator(Nodes.Any).invalidateLayer()`. No-op until the
	 * renderer rewrite uses the Modifier.Node draw pipeline.
	 */
	fun invalidateLayer() { /* no-op */ }
}

/**
 * [LayoutCoordinates] view onto an owning ProjectLayoutNode. Reports the
 * layout's current measured size; other LayoutCoordinates members
 * default to their interface-provided defaults.
 *
 * No `localPositionOf` math is wired — vendored Ruler-driven code that
 * would call it is dormant in our pipeline.
 */
private class LayoutNodeCoordinates(private val fOwner: ProjectLayoutNode) : LayoutCoordinates {
	override val size: IntSize
		get() = IntSize(fOwner.width, fOwner.height)
	override val isAttached: Boolean
		get() = true
	override fun localPositionOf(
		inSourceCoordinates: LayoutCoordinates,
		inRelativeToSource: Offset,
	): Offset = inRelativeToSource
}
