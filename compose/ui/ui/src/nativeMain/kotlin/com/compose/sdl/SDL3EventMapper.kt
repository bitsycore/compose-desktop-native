package com.compose.sdl

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import com.compose.sdl.input.LegacyPointerEvent
import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3EventMapper
// ==================

/* Events carry the SDL window id they belong to (0 = unknown — injected test
   events; the loop routes those to its first window). Quit is app-level. */
sealed class AppEvent {
	data object Quit : AppEvent()
	data class Pointer(val event: LegacyPointerEvent, val windowId: UInt = 0u) : AppEvent()
	data class Key(val event: KeyEvent, val windowId: UInt = 0u) : AppEvent()
	data class TextInput(val text: String, val windowId: UInt = 0u) : AppEvent()
	/* IME preedit / composition (SDL_EVENT_TEXT_EDITING). Empty text = composition
	   cleared, so it's dispatched even when blank (unlike committed TextInput). */
	data class TextEditing(val text: String, val windowId: UInt = 0u) : AppEvent()
	data class MouseWheel(val x: Int, val y: Int, val deltaX: Float, val deltaY: Float, val windowId: UInt = 0u) : AppEvent()
	/* The pointer left the window (SDL_EVENT_WINDOW_MOUSE_LEAVE). The loop clears
	   the window's hover state so a widget doesn't stay highlighted after the cursor
	   exits (SDL then sends no motion, so nothing else would clear it). */
	data class PointerExit(val windowId: UInt = 0u) : AppEvent()
	data class WindowResized(val windowId: UInt = 0u) : AppEvent()
	/* The user asked THIS window to close (OS close button). The loop routes it
	   through the window's close-interception then its onCloseRequest. */
	data class WindowClose(val windowId: UInt = 0u) : AppEvent()
	/* The OS invalidated the window contents (expose / un-minimise / focus
	   regain). The render-on-demand main loop uses it to force a frame even
	   though no state changed. */
	data class RedrawNeeded(val windowId: UInt = 0u) : AppEvent()
	/* Focus / visibility transitions driving the window's Lifecycle (Compose
	   Desktop mapping: focused → RESUMED, visible unfocused → STARTED,
	   hidden / minimised → CREATED). Null = "this event doesn't change it".
	   Also implies RedrawNeeded for the shown/focused transitions. */
	data class WindowActivation(
		val windowId: UInt = 0u,
		val focused: Boolean? = null,
		val visible: Boolean? = null,
	) : AppEvent()
	/* A drag-and-drop from another app hit the window. SDL fires these as a
	   BEGIN → (FILE / TEXT)* + POSITION* → COMPLETE sequence; the loop
	   forwards each to the window's Sdl3DragAndDropOwner via ComposeRootHost. */
	data class Drop(
		val windowId: UInt,
		val phase: DropPhase,
		val x: Float = 0f,
		val y: Float = 0f,
		val data: String? = null,
	) : AppEvent()

	enum class DropPhase { BEGIN, POSITION, FILE, TEXT, COMPLETE }
}

fun pollEvents(): List<AppEvent> {
	val events = mutableListOf<AppEvent>()
	memScoped {
		val sdlEvent = alloc<SDL_Event>()
		while (SDL_PollEvent(sdlEvent.ptr)) {
			val mapped = mapEvent(sdlEvent)
			if (mapped != null) events.add(mapped)
		}
	}
	return events
}

private fun mapEvent(e: SDL_Event): AppEvent? {
	val type = e.type
	return when (type) {
		SDL_EVENT_QUIT -> AppEvent.Quit

		SDL_EVENT_MOUSE_BUTTON_DOWN -> {
			val mb = e.button
			AppEvent.Pointer(LegacyPointerEvent(
				x = mb.x.toInt(), y = mb.y.toInt(),
				type = PointerEventType.Press,
				button = mapButton(mb.button)
			), mb.windowID)
		}

		SDL_EVENT_MOUSE_BUTTON_UP -> {
			val mb = e.button
			AppEvent.Pointer(LegacyPointerEvent(
				x = mb.x.toInt(), y = mb.y.toInt(),
				type = PointerEventType.Release,
				button = mapButton(mb.button)
			), mb.windowID)
		}

		SDL_EVENT_MOUSE_MOTION -> {
			val mm = e.motion
			AppEvent.Pointer(LegacyPointerEvent(
				x = mm.x.toInt(), y = mm.y.toInt(),
				type = PointerEventType.Move
			), mm.windowID)
		}

		SDL_EVENT_KEY_DOWN -> mapKey(e.key, KeyEventType.KeyDown)
		SDL_EVENT_KEY_UP -> mapKey(e.key, KeyEventType.KeyUp)

		SDL_EVENT_WINDOW_RESIZED -> AppEvent.WindowResized(e.window.windowID)
		// Backing-store size / DPR changes flow through the same resize path.
		SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED -> AppEvent.WindowResized(e.window.windowID)
		SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED -> AppEvent.WindowResized(e.window.windowID)

		// Per-window close (OS close button). SDL also fires QUIT after the
		// LAST window's close request — the loop treats that as app exit only
		// when no window vetoed.
		SDL_EVENT_WINDOW_CLOSE_REQUESTED -> AppEvent.WindowClose(e.window.windowID)

		// Content invalidations — the idle-skipping main loop must render a
		// frame after these even though no Compose state changed.
		SDL_EVENT_WINDOW_EXPOSED,
		SDL_EVENT_WINDOW_MAXIMIZED -> AppEvent.RedrawNeeded(e.window.windowID)

		// Focus / visibility → the window's Lifecycle state (the loop also
		// treats these as redraw triggers, preserving the old RedrawNeeded
		// behaviour of SHOWN / RESTORED / FOCUS_GAINED).
		SDL_EVENT_WINDOW_FOCUS_GAINED -> AppEvent.WindowActivation(e.window.windowID, focused = true)
		SDL_EVENT_WINDOW_FOCUS_LOST -> AppEvent.WindowActivation(e.window.windowID, focused = false)
		SDL_EVENT_WINDOW_SHOWN,
		SDL_EVENT_WINDOW_RESTORED -> AppEvent.WindowActivation(e.window.windowID, visible = true)
		SDL_EVENT_WINDOW_HIDDEN,
		SDL_EVENT_WINDOW_MINIMIZED -> AppEvent.WindowActivation(e.window.windowID, visible = false)

		// Pointer left the window — clear hover (SDL sends no further motion).
		SDL_EVENT_WINDOW_MOUSE_LEAVE -> AppEvent.PointerExit(e.window.windowID)

		SDL_EVENT_TEXT_INPUT -> {
			val vText = e.text.text?.toKString().orEmpty()
			if (vText.isEmpty()) null else AppEvent.TextInput(vText, e.text.windowID)
		}

		SDL_EVENT_TEXT_EDITING ->
			AppEvent.TextEditing(e.edit.text?.toKString().orEmpty(), e.edit.windowID)

		SDL_EVENT_MOUSE_WHEEL -> {
			val mw = e.wheel
			AppEvent.MouseWheel(
				x = mw.mouse_x.toInt(),
				y = mw.mouse_y.toInt(),
				deltaX = mw.x,
				deltaY = mw.y,
				windowId = mw.windowID,
			)
		}

		// OS drag-and-drop into the window. SDL emits these as a session:
		// BEGIN, then FILE and/or TEXT and/or POSITION events, then COMPLETE.
		// x/y are window-local logical points (multiplied by DPR at dispatch);
		// data is the file path / text, NULL for BEGIN / COMPLETE / POSITION.
		SDL_EVENT_DROP_BEGIN -> AppEvent.Drop(e.drop.windowID, AppEvent.DropPhase.BEGIN)
		SDL_EVENT_DROP_POSITION -> AppEvent.Drop(
			windowId = e.drop.windowID,
			phase = AppEvent.DropPhase.POSITION,
			x = e.drop.x, y = e.drop.y,
		)
		SDL_EVENT_DROP_FILE -> AppEvent.Drop(
			windowId = e.drop.windowID,
			phase = AppEvent.DropPhase.FILE,
			x = e.drop.x, y = e.drop.y,
			data = e.drop.data?.toKString(),
		)
		SDL_EVENT_DROP_TEXT -> AppEvent.Drop(
			windowId = e.drop.windowID,
			phase = AppEvent.DropPhase.TEXT,
			x = e.drop.x, y = e.drop.y,
			data = e.drop.data?.toKString(),
		)
		SDL_EVENT_DROP_COMPLETE -> AppEvent.Drop(e.drop.windowID, AppEvent.DropPhase.COMPLETE)

		else -> null
	}
}

private fun mapKey(inKk: SDL_KeyboardEvent, inType: KeyEventType): AppEvent.Key {
	val vScancode = inKk.scancode.toInt()
	val vMod = inKk.mod.toInt()
	return AppEvent.Key(KeyEvent(
		// Letters resolve from the layout-aware KEYCODE, so a shortcut lands on the
		// key the user actually sees: Ctrl+A is the 'A' key on AZERTY / QWERTZ, not
		// the physical QWERTY-A position. Non-letters fall back to the positional
		// scancode (arrows / F-keys / numpad / punctuation are layout-independent).
		key = kKeyForKeycode(inKk.key.toInt()) ?: kKeyForScancode(vScancode),
		type = inType,
		// Deliberately 0: SDL keycodes are UNSHIFTED (lowercase only, no numpad
		// digits, base layout chars) — deriving typed characters from them broke
		// Shift/caps and the numeric pad. Committed characters arrive through
		// SDL_EVENT_TEXT_INPUT instead; ComposeRootHost.dispatchTextInput
		// re-dispatches them as synthetic typed KeyEvents, and codePoint = 0
		// here guarantees the physical KeyDown can't double-insert.
		codePoint = 0,
		isShiftPressed = (vMod and SDL_KMOD_SHIFT.toInt()) != 0,
		isCtrlPressed = (vMod and SDL_KMOD_CTRL.toInt()) != 0,
		isAltPressed = (vMod and SDL_KMOD_ALT.toInt()) != 0,
		isMetaPressed = (vMod and SDL_KMOD_GUI.toInt()) != 0,
	), inKk.windowID)
}

private fun mapButton(b: UByte): PointerButton = when (b.toInt()) {
	1 -> PointerButton.Primary
	2 -> PointerButton.Tertiary
	3 -> PointerButton.Secondary
	else -> PointerButton.Primary
}

/* SDL_Keycode (layout-aware) → Key, for Latin LETTERS only. The keycode reflects
   the active layout, so this maps to the letter the user perceives regardless of
   the key's physical position — the fix for Ctrl+<letter> shortcuts on AZERTY /
   QWERTZ. Uppercase is folded to lowercase (some SDL builds apply Shift to the
   keycode). Returns null for non-letters, which the caller resolves positionally
   via kKeyForScancode; non-Latin layouts also fall through to positional. */
private fun kKeyForKeycode(inKeycode: Int): Key? {
	val vLower = if (inKeycode in 'A'.code..'Z'.code) inKeycode + 32 else inKeycode
	return when (vLower) {
		'a'.code -> Key.A; 'b'.code -> Key.B; 'c'.code -> Key.C; 'd'.code -> Key.D
		'e'.code -> Key.E; 'f'.code -> Key.F; 'g'.code -> Key.G; 'h'.code -> Key.H
		'i'.code -> Key.I; 'j'.code -> Key.J; 'k'.code -> Key.K; 'l'.code -> Key.L
		'm'.code -> Key.M; 'n'.code -> Key.N; 'o'.code -> Key.O; 'p'.code -> Key.P
		'q'.code -> Key.Q; 'r'.code -> Key.R; 's'.code -> Key.S; 't'.code -> Key.T
		'u'.code -> Key.U; 'v'.code -> Key.V; 'w'.code -> Key.W; 'x'.code -> Key.X
		'y'.code -> Key.Y; 'z'.code -> Key.Z
		else -> null
	}
}

// ==================
// MARK: SDL3 scancode → Key map
// ==================

/* SDL_SCANCODE_* values (from sdl3/SDL_scancode.h) → vendored Key.
   Returns Key.Unknown for codes we don't have a named slot for. */
private fun kKeyForScancode(inScancode: Int): Key = when (inScancode) {
	// Letters (4..29)
	4 -> Key.A;  5 -> Key.B;  6 -> Key.C;  7 -> Key.D
	8 -> Key.E;  9 -> Key.F;  10 -> Key.G; 11 -> Key.H
	12 -> Key.I; 13 -> Key.J; 14 -> Key.K; 15 -> Key.L
	16 -> Key.M; 17 -> Key.N; 18 -> Key.O; 19 -> Key.P
	20 -> Key.Q; 21 -> Key.R; 22 -> Key.S; 23 -> Key.T
	24 -> Key.U; 25 -> Key.V; 26 -> Key.W; 27 -> Key.X
	28 -> Key.Y; 29 -> Key.Z

	// Top-row digits (30..39)
	30 -> Key.One;   31 -> Key.Two; 32 -> Key.Three; 33 -> Key.Four
	34 -> Key.Five;  35 -> Key.Six; 36 -> Key.Seven; 37 -> Key.Eight
	38 -> Key.Nine;  39 -> Key.Zero

	// Control / whitespace
	40 -> Key.Enter           // SDL_SCANCODE_RETURN
	41 -> Key.Escape
	42 -> Key.Backspace
	43 -> Key.Tab
	44 -> Key.Spacebar

	// Punctuation
	45 -> Key.Minus
	46 -> Key.Equals
	47 -> Key.LeftBracket
	48 -> Key.RightBracket
	49 -> Key.Backslash
	51 -> Key.Semicolon
	52 -> Key.Apostrophe
	53 -> Key.Grave
	54 -> Key.Comma
	55 -> Key.Period
	56 -> Key.Slash
	57 -> Key.CapsLock

	// Function keys (58..69 = F1..F12)
	58 -> Key.F1;  59 -> Key.F2;  60 -> Key.F3;  61 -> Key.F4
	62 -> Key.F5;  63 -> Key.F6;  64 -> Key.F7;  65 -> Key.F8
	66 -> Key.F9;  67 -> Key.F10; 68 -> Key.F11; 69 -> Key.F12

	70 -> Key.PrintScreen
	71 -> Key.ScrollLock
	72 -> Key.Break              // PAUSE
	73 -> Key.Insert
	74 -> Key.MoveHome
	75 -> Key.PageUp
	76 -> Key.Delete
	77 -> Key.MoveEnd
	78 -> Key.PageDown

	// Arrows
	79 -> Key.DirectionRight
	80 -> Key.DirectionLeft
	81 -> Key.DirectionDown
	82 -> Key.DirectionUp

	// Numpad
	83 -> Key.NumLock
	84 -> Key.NumPadDivide
	85 -> Key.NumPadMultiply
	86 -> Key.NumPadSubtract
	87 -> Key.NumPadAdd
	88 -> Key.NumPadEnter
	89 -> Key.NumPad1; 90 -> Key.NumPad2; 91 -> Key.NumPad3
	92 -> Key.NumPad4; 93 -> Key.NumPad5; 94 -> Key.NumPad6
	95 -> Key.NumPad7; 96 -> Key.NumPad8; 97 -> Key.NumPad9
	98 -> Key.NumPad0
	99 -> Key.NumPadDot
	103 -> Key.NumPadEquals

	// Modifier keys
	224 -> Key.CtrlLeft
	225 -> Key.ShiftLeft
	226 -> Key.AltLeft
	227 -> Key.MetaLeft
	228 -> Key.CtrlRight
	229 -> Key.ShiftRight
	230 -> Key.AltRight
	231 -> Key.MetaRight

	else -> Key.Unknown
}
