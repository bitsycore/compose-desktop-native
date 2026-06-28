package androidx.compose.ui.layout

import com.compose.desktop.native.node.LayoutNode

// ==================
// MARK: Placeable
// ==================

/**
 * The measured result of asking a Measurable to measure. Carries width
 * and height, plus a [placeAt] that positions the underlying LayoutNode
 * inside its parent. Placement is deferred — measure() returns the
 * Placeable, then the MeasurePolicy's `layout { }` block calls placeAt(x, y)
 * inside its placement closure.
 */
abstract class Placeable {

	abstract val width: Int
	abstract val height: Int

	/**
	 * Place the underlying node at ([inX], [inY]) in its parent's coordinate
	 * space. Coordinates are in logical points (the layout pass runs at
	 * logical resolution; HiDPI scaling happens in the renderer).
	 */
	abstract fun placeAt(inX: Int, inY: Int)

	/**
	 * Receiver scope for a `MeasureScope.layout { }` placement block.
	 * Placement happens by calling `placeable.placeAt(x, y)` directly
	 * (Placeable.placeAt is public). Nested inside Placeable for
	 * upstream-API parity; the single Default instance is the receiver
	 * passed into every `layout { }` block.
	 */
	/**
	 * Placement-block receiver. Phase 4d brings the public surface to
	 * upstream's shape: extends [androidx.compose.ui.unit.Density],
	 * exposes `parentWidth` + `parentLayoutDirection` for RTL mirroring,
	 * carries the `density` + `fontScale` Density members. Defaults are
	 * sensible no-ops — RTL mirroring is disabled (parentWidth = 0) and
	 * density / fontScale are 1f.
	 */
	@androidx.compose.ui.layout.PlacementScopeMarker
	abstract class PlacementScope : androidx.compose.ui.unit.Density {

		override val density: Float
			get() = 1f

		override val fontScale: Float
			get() = 1f

		/** RTL mirroring origin. 0 disables mirroring. */
		protected open val parentWidth: Int
			get() = 0

		/** Layout direction of the parent. Ltr means no mirroring. */
		protected open val parentLayoutDirection: androidx.compose.ui.unit.LayoutDirection
			get() = androidx.compose.ui.unit.LayoutDirection.Ltr

		/**
		 * Phase 4b: read the current value of [Ruler] in this PlacementScope,
		 * or [defaultValue] if not provided. Mirrors upstream
		 * `Placeable.PlacementScope.kt` line 199 (open fun with the same
		 * default). Vendored Ruler.mergeRulerValues calls this — but no
		 * Ruler instances are constructed in our pipeline, so this is
		 * never invoked at runtime.
		 */
		open fun Ruler.current(@Suppress("UNUSED_PARAMETER") defaultValue: Float): Float = defaultValue

		internal companion object Default : PlacementScope()
	}
}

internal class LayoutNodePlaceable(private val fNode: LayoutNode) : Placeable() {

	override val width: Int get() = fNode.width
	override val height: Int get() = fNode.height
	override fun placeAt(inX: Int, inY: Int) { fNode.place(inX, inY) }
}
