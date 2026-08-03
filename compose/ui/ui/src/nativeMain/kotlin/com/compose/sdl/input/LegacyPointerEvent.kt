package com.compose.sdl.input

import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType

// ==================
// MARK: LegacyPointerEvent (project-only)
// ==================

/** Project-only legacy event class used by the SDL3 input pipeline
   (`SDL3EventMapper` → `AppEvent.Pointer` → `ComposeWindow`'s event loop).
   The name `PointerEvent` is now reserved for upstream's multi-touch
   vendored event (in androidx.compose.ui.input.pointer.PointerEvent);
   this single-pointer (x, y, type, button) shape is the bridge between
   raw SDL events and upstream-shaped PointerInputChange dispatch.

   ComposeWindow constructs an upstream PointerEvent from this for
   delivery to Modifier.pointerInput blocks. */
class LegacyPointerEvent(
	// Kept as Float: SDL delivers sub-pixel cursor coordinates, and on HiDPI the
	// value is multiplied by DPR before dispatch, so truncating to Int here would
	// quantize the caret to whole-DPR steps (2px on a 2x display) near glyph edges.
	val x: Float,
	val y: Float,
	val type: PointerEventType,
	val button: PointerButton = PointerButton.Primary,
) {
	override fun equals(other: Any?): Boolean = other is LegacyPointerEvent &&
		other.x == x && other.y == y && other.type == type && other.button == button
	override fun hashCode(): Int {
		var h = x.hashCode(); h = 31 * h + y.hashCode(); h = 31 * h + type.hashCode(); h = 31 * h + button.hashCode(); return h
	}
	override fun toString(): String =
		"LegacyPointerEvent(x=$x, y=$y, type=$type, button=$button)"
}
