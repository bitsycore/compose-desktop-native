package androidx.compose.ui.graphics

// ==================
// MARK: Shape
// ==================

/* Produces an outline geometry for a given pixel size. The SDL3 renderer
   reads the outline and dispatches to the correct fill/stroke primitive. */
interface Shape {
    fun outline(inWidth: Int, inHeight: Int): Outline
}

// ==================
// MARK: Outline
// ==================

sealed class Outline {
    data class Rectangle(val width: Int, val height: Int) : Outline()
    data class RoundedRect(val width: Int, val height: Int, val cornerRadius: Int) : Outline()
}

// ==================
// MARK: Predefined shapes
// ==================

val RectangleShape: Shape = object : Shape {
    override fun outline(inWidth: Int, inHeight: Int): Outline =
        Outline.Rectangle(inWidth, inHeight)
}
