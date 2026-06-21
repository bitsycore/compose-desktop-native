package androidx.compose.ui.layout

import androidx.compose.ui.node.LayoutNode

// ==================
// MARK: Placeable
// ==================

/* The measured result of asking a Measurable to measure. Carries width
   and height, plus a placeAt() that positions the underlying LayoutNode
   inside its parent. Placement is deferred — measure() returns the
   Placeable, then the MeasurePolicy's layout {} block calls placeAt(x,y)
   inside its placement closure. */
abstract class Placeable {

	abstract val width: Int
	abstract val height: Int

	/* Place the underlying node at (x, y) in its parent's coordinate
	   space. Coordinates are in logical points (the layout pass runs at
	   logical resolution; HiDPI scaling happens in the renderer). */
	abstract fun placeAt(inX: Int, inY: Int)
}

internal class LayoutNodePlaceable(private val fNode: LayoutNode) : Placeable() {

	override val width: Int get() = fNode.width
	override val height: Int get() = fNode.height
	override fun placeAt(inX: Int, inY: Int) { fNode.place(inX, inY) }
}
