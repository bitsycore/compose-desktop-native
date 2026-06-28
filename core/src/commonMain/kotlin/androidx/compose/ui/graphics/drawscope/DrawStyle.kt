package androidx.compose.ui.graphics.drawscope

import androidx.compose.ui.graphics.StrokeCap

// ==================
// MARK: DrawStyle
// ==================

/* How a shape primitive on DrawScope is painted. Mirrors upstream Compose's
   DrawStyle sealed hierarchy. Fill fills the shape interior; Stroke outlines
   it with the configured width and end cap. Defaults are chosen to match
   what upstream picks when no style is supplied (Fill for shapes, butt cap
   for thin strokes). */
sealed class DrawStyle

/* Solid fill — no stroke. The single Fill instance is enough; callers
   compare by reference. */
object Fill : DrawStyle()

/* Stroked outline with the given line width (in logical points) and end-cap
   style. miter is unused for now — round/butt/square cover the practical
   widget cases. */
class Stroke(
	val width: Float,
	val cap: StrokeCap = StrokeCap.Butt,
) : DrawStyle() {
	companion object {
		const val HairlineWidth: Float = 0f
	}

	override fun equals(other: Any?): Boolean =
		other is Stroke && other.width == width && other.cap == cap
	override fun hashCode(): Int = width.hashCode() * 31 + cap.hashCode()
	override fun toString(): String = "Stroke(width=$width, cap=$cap)"
}

// StrokeCap lives in its own vendored file (androidx.compose.ui.graphics.StrokeCap).
