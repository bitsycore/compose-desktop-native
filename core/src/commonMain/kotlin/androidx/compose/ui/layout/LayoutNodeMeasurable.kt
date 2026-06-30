package androidx.compose.ui.layout

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.node.LayoutNode

// ==================
// MARK: LayoutNodeMeasurable
// ==================

/* Default Measurable implementation: forwards to LayoutNode.measure() and
   produces a Placeable that places that same node when asked.

   Implements the IntrinsicMeasurable members from upstream's vendored
   Measurable: our LayoutNode doesn't track intrinsic measurements yet, so
   the min/maxIntrinsic* methods fall back to a full measure(Constraints).
   parentData stays null until we port ParentDataModifierNode. */
internal class LayoutNodeMeasurable(val fNode: LayoutNode) : Measurable {

	/** Project-shape parent data — Row/Column read this to discover
	 *  `LayoutWeightModifier`. Upstream uses ParentDataModifierNode
	 *  dispatch; we forward the project's cached weight modifier directly. */
	override val parentData: Any? get() = fNode.cachedLayoutWeight

	override fun measure(inConstraints: Constraints): Placeable {
		fNode.measure(inConstraints)
		return LayoutNodePlaceable(fNode)
	}

	override fun minIntrinsicWidth(height: Int): Int =
		fNode.measure(Constraints(0, Constraints.Infinity, height, height)).width
	override fun maxIntrinsicWidth(height: Int): Int =
		fNode.measure(Constraints(0, Constraints.Infinity, height, height)).width
	override fun minIntrinsicHeight(width: Int): Int =
		fNode.measure(Constraints(width, width, 0, Constraints.Infinity)).height
	override fun maxIntrinsicHeight(width: Int): Int =
		fNode.measure(Constraints(width, width, 0, Constraints.Infinity)).height
}
