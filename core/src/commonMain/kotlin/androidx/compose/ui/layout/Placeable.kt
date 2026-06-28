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
	@androidx.compose.ui.layout.PlacementScopeMarker
	abstract class PlacementScope {
		internal companion object Default : PlacementScope()
	}
}

internal class LayoutNodePlaceable(private val fNode: LayoutNode) : Placeable() {

	override val width: Int get() = fNode.width
	override val height: Int get() = fNode.height
	override fun placeAt(inX: Int, inY: Int) { fNode.place(inX, inY) }
}
