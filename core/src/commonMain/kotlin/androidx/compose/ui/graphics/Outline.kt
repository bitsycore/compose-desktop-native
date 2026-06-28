package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.boundingRect

// ==================
// MARK: Outline
// ==================

/* Reduced reimplementation of androidx.compose.ui.graphics.Outline. Upstream
   ships extension functions `DrawScope.drawOutline(...)` and
   `Canvas.drawOutline(...)` that require our DrawScope to grow
   `cornerRadius: CornerRadius` / `colorFilter` / `blendMode` parameters
   (a separate pass). The sealed-class shape matches upstream byte-for-byte;
   the renderers read `bounds`, `rect`, `roundRect`, and `path` directly and
   dispatch through their own paint primitives. */
sealed class Outline {
	abstract val bounds: Rect

	/* Rectangular area. */
	class Rectangle(val rect: Rect) : Outline() {
		override val bounds: Rect get() = rect
		override fun equals(other: Any?): Boolean =
			this === other || (other is Rectangle && other.rect == rect)
		override fun hashCode(): Int = rect.hashCode()
	}

	/* Rectangular area with rounded corners (may differ per corner). */
	class Rounded(val roundRect: RoundRect) : Outline() {
		override val bounds: Rect get() = roundRect.boundingRect
		override fun equals(other: Any?): Boolean =
			this === other || (other is Rounded && other.roundRect == roundRect)
		override fun hashCode(): Int = roundRect.hashCode()
	}

	/* Free-form path. */
	class Generic(val path: Path) : Outline() {
		// Path's getBounds() walks the command list; renderers that hit this
		// branch already know the node's layout rect, so we report Rect.Zero
		// here and let the renderer fall back to its own bounds.
		override val bounds: Rect get() = Rect.Zero
		// No equals/hashCode — two outlines built from the same Path shouldn't
		// be considered equal since the Path is mutable.
	}
}
