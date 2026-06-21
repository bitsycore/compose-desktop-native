package androidx.compose.foundation.layout

import androidx.compose.ui.LayoutWeightModifier
import androidx.compose.ui.Modifier

// ==================
// MARK: RowScope / ColumnScope
// ==================

/* Receiver scope for Row's content lambda. Hosts the .weight() extension
   so weighted layout is scope-restricted: callers can't add .weight() to
   a child sitting outside a Row / Column (where it would silently be a
   no-op). The object form (rather than an interface) keeps the call
   site free of an unused parameter. */
object RowScope {

	/* Claim a fraction of the leftover main-axis space. Children without
	   .weight() measure first at their intrinsic size; the remaining
	   width is then split between weighted children proportionally to
	   their weights. fill = true forces the child to fill its allotted
	   slice (the upstream default); fill = false caps it at its preferred
	   size while still claiming that share. weight must be > 0. */
	fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier {
		require(weight > 0f) { "Weight must be > 0 (got $weight)" }
		return this.then(LayoutWeightModifier(weight, fill))
	}
}

/* Receiver scope for Column's content lambda — same .weight() semantics
   as RowScope but the main axis is vertical. */
object ColumnScope {

	fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier {
		require(weight > 0f) { "Weight must be > 0 (got $weight)" }
		return this.then(LayoutWeightModifier(weight, fill))
	}
}
