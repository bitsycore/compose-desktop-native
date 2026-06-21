package androidx.compose.ui.layout

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints

// ==================
// MARK: Measurable
// ==================

/* A child of a Layout, exposed as the only thing a MeasurePolicy can do
   to it: ask it to measure under some constraints. The implementation
   wraps a LayoutNode so the call path lands in the same measure/place
   pipeline used by Row / Column / Box internally. */
interface Measurable {

	/* Run measurement and produce a Placeable carrying the measured size
	   and a placeAt method. */
	fun measure(constraints: Constraints): Placeable
}

/* Default Measurable implementation: forwards to LayoutNode.measure() and
   produces a Placeable that places that same node when asked. */
internal class LayoutNodeMeasurable(private val fNode: LayoutNode) : Measurable {

	override fun measure(constraints: Constraints): Placeable {
		fNode.measure(constraints)
		return LayoutNodePlaceable(fNode)
	}
}
