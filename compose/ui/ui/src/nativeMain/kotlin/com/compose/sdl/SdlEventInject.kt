package com.compose.sdl

import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SdlEventInject
// ==================

/* Test-only: push a synthetic mouse event into SDL's real event queue so it flows
   through the exact live path (pollEvents → AppEvent.Pointer → host.onPointerRaw →
   PointerInputEventProcessor → upstream clickable). Used by the demo's `--clicktest`
   to verify the vendored interaction engine end-to-end. inType: 0=Move 1=Press 2=Release. */
@OptIn(ExperimentalForeignApi::class)
fun injectMouseEvent(inType: Int, inX: Float, inY: Float) {
	memScoped {
		val vEv = alloc<SDL_Event>()
		when (inType) {
			0 -> {
				vEv.type = SDL_EVENT_MOUSE_MOTION
				vEv.motion.which = 0u
				vEv.motion.x = inX
				vEv.motion.y = inY
			}
			1 -> {
				vEv.type = SDL_EVENT_MOUSE_BUTTON_DOWN
				vEv.button.which = 0u
				vEv.button.button = 1u
				vEv.button.down = true
				vEv.button.clicks = 1u
				vEv.button.x = inX
				vEv.button.y = inY
			}
			2 -> {
				vEv.type = SDL_EVENT_MOUSE_BUTTON_UP
				vEv.button.which = 0u
				vEv.button.button = 1u
				vEv.button.down = false
				vEv.button.clicks = 1u
				vEv.button.x = inX
				vEv.button.y = inY
			}
		}
		SDL_PushEvent(vEv.ptr)
	}
}

/* Test-only: push a synthetic SDL_EVENT_TEXT_INPUT (typed character[s]). SDL_TextInputEvent.text
   is a const char* that SDL does NOT copy on push, so the UTF-8 buffer is allocated on nativeHeap
   and intentionally leaked — it must stay valid until the event is polled next frame. */
@OptIn(ExperimentalForeignApi::class)
fun injectTextInput(inText: String) {
	val vBytes = inText.encodeToByteArray()
	val vBuf = nativeHeap.allocArray<ByteVar>(vBytes.size + 1)
	for (i in vBytes.indices) vBuf[i] = vBytes[i]
	vBuf[vBytes.size] = 0
	memScoped {
		val vEv = alloc<SDL_Event>()
		vEv.type = SDL_EVENT_TEXT_INPUT
		vEv.text.text = vBuf
		SDL_PushEvent(vEv.ptr)
	}
}

/* Test-only: push a synthetic mouse-wheel event at (inX,inY) with wheel deltas (inDeltaX,inDeltaY).
   SDL convention: deltaY positive = wheel up. */
@OptIn(ExperimentalForeignApi::class)
fun injectWheel(inX: Float, inY: Float, inDeltaX: Float, inDeltaY: Float) {
	memScoped {
		val vEv = alloc<SDL_Event>()
		vEv.type = SDL_EVENT_MOUSE_WHEEL
		vEv.wheel.mouse_x = inX
		vEv.wheel.mouse_y = inY
		vEv.wheel.x = inDeltaX
		vEv.wheel.y = inDeltaY
		SDL_PushEvent(vEv.ptr)
	}
}

/* Test-only: push a synthetic key event. inScancode is an SDL_SCANCODE_* value (e.g. 42=Backspace). */
@OptIn(ExperimentalForeignApi::class)
fun injectKey(inScancode: Int, inDown: Boolean) {
	memScoped {
		val vEv = alloc<SDL_Event>()
		vEv.type = if (inDown) SDL_EVENT_KEY_DOWN else SDL_EVENT_KEY_UP
		vEv.key.scancode = inScancode.toUInt()
		vEv.key.down = inDown
		vEv.key.mod = 0u
		vEv.key.key = 0u
		SDL_PushEvent(vEv.ptr)
	}
}
