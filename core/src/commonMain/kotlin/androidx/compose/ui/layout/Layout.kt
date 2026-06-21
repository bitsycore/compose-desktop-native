package androidx.compose.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeApplier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.node.MeasurePolicy as InternalMeasurePolicy

// ==================
// MARK: MeasurePolicy (public)
// ==================

/* User-facing measure policy. The body wraps each child as a Measurable,
   measures them, and calls layout(w, h) { ... } to declare the final
   size and where to place each Placeable. */
fun interface MeasurePolicy {

	fun MeasureScope.measure(
		inMeasurables: List<Measurable>,
		inConstraints: Constraints,
	): MeasureResult
}

// ==================
// MARK: Layout composable
// ==================

/* Build a custom layout from a content lambda + a MeasurePolicy. Lands
   on the same LayoutNode pipeline used by Row / Column / Box, so anything
   you build with Layout participates in modifiers, hit-testing, etc.

   Example:
       Layout(content = { Text("a"); Text("b") }) { measurables, c ->
           val placeables = measurables.map { it.measure(c) }
           val w = placeables.sumOf { it.width }
           val h = placeables.maxOf { it.height }
           layout(w, h) {
               var x = 0
               placeables.forEach { it.placeAt(x, 0); x += it.width }
           }
       } */
@Composable
fun Layout(
	content: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	measurePolicy: MeasurePolicy,
) {
	ComposeNode<LayoutNode, NodeApplier>(
		factory = { LayoutNode() },
		update = {
			set(modifier) { this.modifier = it }
			set(measurePolicy) {
				this.measurePolicy = adaptToInternal(it)
			}
		},
		content = content,
	)
}

/* Bridge the public MeasurePolicy onto the existing internal one. The
   internal interface gets the LayoutNode directly; we wrap each of its
   children as a LayoutNodeMeasurable, build a MeasureScope, run the
   user's body, place the children, and return the resolved IntSize. */
internal fun adaptToInternal(inPolicy: MeasurePolicy): InternalMeasurePolicy =
	InternalMeasurePolicy { vNode, vConstraints ->
		val vMeasurables: List<Measurable> = vNode.children.map { LayoutNodeMeasurable(it) }
		val vScope = MeasureScopeImpl()
		val vResult = inPolicy.run { vScope.measure(vMeasurables, vConstraints) }
		vResult.placeChildren()
		IntSize(vResult.width, vResult.height)
	}
