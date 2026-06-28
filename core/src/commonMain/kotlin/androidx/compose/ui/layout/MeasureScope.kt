package androidx.compose.ui.layout

// ==================
// MARK: MeasureScope / MeasureResult
// ==================

/* Receiver scope for a MeasurePolicy's measure() body. Provides the
   layout() builder that bundles the resolved parent size with a
   placement block to be invoked after measurement returns. */
interface MeasureScope {

	/* Declare this layout's final size and the placement block to run.
	   The block receives a Placeable.PlacementScope where Placeables can
	   be positioned via placeAt(x, y). */
	fun layout(
		inWidth: Int,
		inHeight: Int,
		inPlacement: Placeable.PlacementScope.() -> Unit,
	): MeasureResult
}

/* The product of a MeasurePolicy: the layout's resolved size plus the
   deferred placement block. The Layout composable's internal adapter
   calls placeChildren() right after measure() returns. */
interface MeasureResult {

	val width: Int
	val height: Int
	fun placeChildren()
}

internal class MeasureScopeImpl : MeasureScope {

	override fun layout(
		inWidth: Int,
		inHeight: Int,
		inPlacement: Placeable.PlacementScope.() -> Unit,
	): MeasureResult = object : MeasureResult {
		override val width: Int = inWidth
		override val height: Int = inHeight
		override fun placeChildren() { Placeable.PlacementScope.inPlacement() }
	}
}
