package androidx.compose.ui.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints

// ==================
// MARK: LayoutModifier shim
// ==================

/**
 * Phase 2 shim for upstream `androidx.compose.ui.layout.LayoutModifier`.
 *
 * Real upstream LayoutModifier.kt (283 lines) declares the legacy
 * Modifier.Element-based layout-modifier interface plus a 5-overload
 * intrinsic-measurement family — all referencing
 * `androidx.compose.ui.node.LayoutModifierNode` + `ModifierNodeElement`
 * (Phase 3 territory). Vendored LayoutAwareModifierNode imports
 * `LayoutModifier` only for KDoc cross-references, so an empty marker
 * interface satisfies the import. Delete when the real LayoutModifier.kt
 * is vendored in Phase 3.
 */
interface LayoutModifier : Modifier.Element

// ==================
// MARK: LayoutModifierElement
// ==================

/**
 * `ModifierNodeElement` factory for the project's `Modifier.layout { … }`
 * intercept. Wraps a child's measurement in user code: given the child as
 * a [Measurable], the user runs `measure()`, computes a new size, and
 * returns it via `layout(width, height) { /* place */ }`. The Box / Row /
 * Column that hosts this node sees the size declared by the modifier
 * rather than the child's natural size.
 *
 * The project's `LayoutNode.measure` pipeline reads this element directly
 * via `findLayoutModifier()` (a `foldIn` over the chain) and invokes
 * [onMeasure] — the Modifier.Node draw/measure lifecycle stays dormant
 * until the renderer rewrite drives it.
 */
internal class LayoutModifierElement(
	val onMeasure: MeasureScope.(Measurable, Constraints) -> MeasureResult,
) : ModifierNodeElement<LayoutModifierNodeImpl>() {
	override fun create() = LayoutModifierNodeImpl(onMeasure)
	override fun update(node: LayoutModifierNodeImpl) { node.onMeasure = onMeasure }
	override fun hashCode(): Int = onMeasure.hashCode()
	override fun equals(other: Any?): Boolean =
		other is LayoutModifierElement && other.onMeasure === onMeasure
}

/** Paired `Modifier.Node` for [LayoutModifierElement] — implements upstream LayoutModifierNode. */
internal class LayoutModifierNodeImpl(
	var onMeasure: MeasureScope.(Measurable, Constraints) -> MeasureResult,
) : Modifier.Node(), androidx.compose.ui.node.LayoutModifierNode {
	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult = onMeasure(measurable, constraints)
}

// ==================
// MARK: Modifier.layout
// ==================

/**
 * Intercept the wrapped child's measure pass. Inside the body you get the
 * child as a [Measurable] plus the incoming [Constraints]; produce a
 * [MeasureResult] via `layout(w, h) { it.placeAt(x, y) }`. Useful for
 * shifting child position by something computed from its measured size,
 * or for forcing a child to a specific size while keeping its place call.
 */
fun Modifier.layout(
	measure: MeasureScope.(Measurable, Constraints) -> MeasureResult,
): Modifier = this.then(LayoutModifierElement(measure))
