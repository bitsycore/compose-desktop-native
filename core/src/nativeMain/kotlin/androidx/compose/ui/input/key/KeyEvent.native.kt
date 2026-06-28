package androidx.compose.ui.input.key

import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltGraphPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed

// ==================
// MARK: KeyEvent native actual
// ==================

/* Mirror of upstream's skikoMain KeyEvent actuals — NativeKeyEvent is just
   `Any` so we can stash an InternalKeyEvent inside the value-class wrapper,
   then extension properties read fields back through `internal`.

   The internal modifier carrier is PointerKeyboardModifiers (vendored as
   part of PointerEvent) — reuses the same packed-Int model used by mouse
   events. */

actual typealias NativeKeyEvent = Any

internal data class InternalKeyEvent(
	val key: Key,
	val type: KeyEventType,
	val codePoint: Int,
	val modifiers: PointerKeyboardModifiers,
	val nativeEvent: Any? = null,
)

internal val KeyEvent.internal: InternalKeyEvent
	get() = nativeKeyEvent as InternalKeyEvent

actual val KeyEvent.key: Key get() = internal.key
actual val KeyEvent.utf16CodePoint: Int get() = internal.codePoint
actual val KeyEvent.type: KeyEventType get() = internal.type
actual val KeyEvent.isAltPressed: Boolean
	get() = internal.modifiers.isAltPressed || internal.modifiers.isAltGraphPressed
actual val KeyEvent.isCtrlPressed: Boolean get() = internal.modifiers.isCtrlPressed
actual val KeyEvent.isMetaPressed: Boolean get() = internal.modifiers.isMetaPressed
actual val KeyEvent.isShiftPressed: Boolean get() = internal.modifiers.isShiftPressed

// ==================
// MARK: KeyEvent factory
// ==================

/* Project factory used by SDL3EventMapper — wraps the internal carrier in a
   KeyEvent value class. Matches the skiko `KeyEvent(...)` factory shape. */
fun KeyEvent(
	key: Key,
	type: KeyEventType,
	codePoint: Int = 0,
	isCtrlPressed: Boolean = false,
	isMetaPressed: Boolean = false,
	isAltPressed: Boolean = false,
	isShiftPressed: Boolean = false,
	nativeEvent: Any? = null,
): KeyEvent = KeyEvent(
	nativeKeyEvent = InternalKeyEvent(
		key = key,
		type = type,
		codePoint = codePoint,
		modifiers = PointerKeyboardModifiers(
			isCtrlPressed = isCtrlPressed,
			isMetaPressed = isMetaPressed,
			isAltPressed = isAltPressed,
			isShiftPressed = isShiftPressed,
		),
		nativeEvent = nativeEvent,
	),
)
