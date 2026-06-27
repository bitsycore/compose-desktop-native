package androidx.compose.ui.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints

// ==================
// MARK: LayoutModifierElement
// ==================

/* Wraps a child's measurement in user code: given the child as a
   Measurable, the user runs measure(), computes a new size, and returns
   it via layout(width, height) { /* place */ }. The Box / Row / Column
   that hosts this node sees the size declared by the modifier rather
   than the child's natural size. */
internal class LayoutModifierElement(
	val onMeasure: MeasureScope.(Measurable, Constraints) -> MeasureResult,
) : Modifier.Element

// ==================
// MARK: Modifier.layout
// ==================

/* Intercept the wrapped child's measure pass. Inside the body you get
   the child as a Measurable plus the incoming Constraints; produce a
   MeasureResult via layout(w, h) { it.placeAt(x, y) }. Useful for
   shifting child position by something computed from its measured size,
   or for forcing a child to a specific size while keeping its place call. */
fun Modifier.layout(
	measure: MeasureScope.(Measurable, Constraints) -> MeasureResult,
): Modifier = this.then(LayoutModifierElement(measure))
