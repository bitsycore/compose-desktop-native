package androidx.compose.foundation.shape

import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import kotlin.math.min

// ==================
// MARK: CircleShape
// ==================

val CircleShape: Shape = object : Shape {
	override fun outline(inWidth: Int, inHeight: Int): Outline =
		Outline.RoundedRect(inWidth, inHeight, min(inWidth, inHeight) / 2)
}

// ==================
// MARK: RoundedCornerShape
// ==================

/* Uniform-corner rounded rectangle. Material 1 buttons use 4.dp.
   NOTE: official RoundedCornerShape is a CornerBasedShape with per-corner
   CornerSize; this is a reduced uniform-radius impl (see CLAUDE.md). */
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

/* Convenience matching Compose's RoundedCornerShape(Int) (percent of the
   shorter side). 50 = pill / circle. */
fun RoundedCornerShape(percent: Int): Shape = object : Shape {
	override fun outline(inWidth: Int, inHeight: Int): Outline {
		val vR = (min(inWidth, inHeight) * percent.coerceIn(0, 50)) / 100
		return if (vR <= 0) Outline.Rectangle(inWidth, inHeight)
		else Outline.RoundedRect(inWidth, inHeight, vR)
	}
}
