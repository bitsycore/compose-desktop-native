package androidx.compose.ui.layout

import androidx.compose.ui.unit.Constraints
import com.compose.desktop.native.node.ProjectLayoutNode

// ==================
// MARK: LayoutNodeMeasurable
// ==================

/* Default Measurable implementation: forwards to ProjectLayoutNode.measure() and
   produces a Placeable that places that same node when asked.

   Implements the IntrinsicMeasurable members from upstream's vendored
   Measurable: our ProjectLayoutNode doesn't track intrinsic measurements yet, so
   the min/maxIntrinsic* methods fall back to a full measure(Constraints).
   parentData stays null until we port ParentDataModifierNode. */
internal class LayoutNodeMeasurable(val fNode: ProjectLayoutNode) : Measurable {

	/** Parent data exposed to the parent's MeasurePolicy — the value folded
	 *  from the chain's `ParentDataModifierNode`s (upstream contract). This is
	 *  what vendored Row/Column/Box read as `measurable.parentData` for
	 *  `weight`/`fill`, `align`/`matchParentSize`, and `layoutId`. */
	override val parentData: Any? get() = fNode.cachedParentData

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
