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

		/**
		 * Phase 4f: upstream-shape `place(x, y)` extension on Placeable.
		 * Mirrors upstream `Placeable.kt` line 245. Our Placeable.placeAt
		 * is a public 2-arg `(x: Int, y: Int)` instead of upstream's
		 * protected 3-arg `(position: IntOffset, zIndex: Float, layerBlock)`,
		 * so this thin wrapper forwards `(x, y)` directly. `zIndex` is
		 * accepted-and-ignored — our renderer reads z-order from a
		 * separate ZIndexModifier on the LayoutNode.
		 */
		fun Placeable.place(
			x: Int,
			y: Int,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
		) {
			placeAt(x, y)
		}

		/** [place(x, y, zIndex)] but takes an [IntOffset] position. */
		fun Placeable.place(
			position: androidx.compose.ui.unit.IntOffset,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
		) {
			placeAt(position.x, position.y)
		}

		/**
		 * RTL-aware placement. If `parentLayoutDirection == Rtl`, mirrors
		 * the x coordinate around `parentWidth`. With our defaults
		 * (parentWidth = 0, parentLayoutDirection = Ltr) this is identical
		 * to [place]. Vendored downstream code (Box / Column / Row) calls
		 * `placeRelative` in places where Ltr/Rtl handling matters.
		 */
		fun Placeable.placeRelative(
			x: Int,
			y: Int,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
		) {
			val vX =
				if (parentLayoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr || parentWidth == 0) x
				else parentWidth - width - x
			placeAt(vX, y)
		}

		/** [placeRelative(x, y, zIndex)] but takes an [IntOffset] position. */
		fun Placeable.placeRelative(
			position: androidx.compose.ui.unit.IntOffset,
			zIndex: Float = 0f,
		) {
			placeRelative(position.x, position.y, zIndex)
		}

		internal companion object Default : PlacementScope()
	}
}

internal class LayoutNodePlaceable(private val fNode: LayoutNode) : Placeable() {

	override val width: Int get() = fNode.width
	override val height: Int get() = fNode.height
	override fun placeAt(inX: Int, inY: Int) { fNode.place(inX, inY) }
}
