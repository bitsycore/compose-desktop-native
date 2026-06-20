package androidx.compose.ui.graphics

import androidx.compose.ui.unit.Dp
import kotlin.math.min

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

val CircleShape: Shape = object : Shape {
    override fun outline(inWidth: Int, inHeight: Int): Outline =
        Outline.RoundedRect(inWidth, inHeight, min(inWidth, inHeight) / 2)
}

/* Uniform-corner rounded rectangle. Material 1 buttons use 4.dp. */
class RoundedCornerShape(private val radius: Dp) : Shape {
    override fun outline(inWidth: Int, inHeight: Int): Outline {
        val vR = radius.value.toInt()
        return if (vR <= 0) Outline.Rectangle(inWidth, inHeight)
        else Outline.RoundedRect(inWidth, inHeight, vR)
    }

    override fun equals(other: Any?): Boolean =
        other is RoundedCornerShape && other.radius == radius

    override fun hashCode(): Int = radius.hashCode()
}

/* Convenience constructor matching Compose's RoundedCornerShape(Int) (percent
   of the shorter side). 50 = pill / circle. */
fun RoundedCornerShape(percent: Int): Shape = object : Shape {
    override fun outline(inWidth: Int, inHeight: Int): Outline {
        val vR = (min(inWidth, inHeight) * percent.coerceIn(0, 50)) / 100
        return if (vR <= 0) Outline.Rectangle(inWidth, inHeight)
        else Outline.RoundedRect(inWidth, inHeight, vR)
    }
}
