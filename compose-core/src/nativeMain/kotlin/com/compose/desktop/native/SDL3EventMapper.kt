package com.compose.desktop.native

import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3EventMapper
// ==================

sealed class AppEvent {
    data object Quit : AppEvent()
    data class Pointer(val event: PointerEvent) : AppEvent()
    data class Key(val event: KeyEvent) : AppEvent()
    data class TextInput(val text: String) : AppEvent()
    data class MouseWheel(val x: Int, val y: Int, val deltaX: Float, val deltaY: Float) : AppEvent()
    data object WindowResized : AppEvent()
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
            AppEvent.Pointer(PointerEvent(
                x = mb.x.toInt(), y = mb.y.toInt(),
                type = PointerEventType.Press,
                button = mapButton(mb.button)
            ))
        }

        SDL_EVENT_MOUSE_BUTTON_UP -> {
            val mb = e.button
            AppEvent.Pointer(PointerEvent(
                x = mb.x.toInt(), y = mb.y.toInt(),
                type = PointerEventType.Release,
                button = mapButton(mb.button)
            ))
        }

        SDL_EVENT_MOUSE_MOTION -> {
            val mm = e.motion
            AppEvent.Pointer(PointerEvent(
                x = mm.x.toInt(), y = mm.y.toInt(),
                type = PointerEventType.Move
            ))
        }

        SDL_EVENT_KEY_DOWN -> {
            val kk = e.key
            AppEvent.Key(KeyEvent(
                keyCode = kk.scancode.toInt(),
                char = null,
                type = KeyEventType.Down,
                modifiers = mapKeyMods(kk.mod)
            ))
        }

        SDL_EVENT_KEY_UP -> {
            val kk = e.key
            AppEvent.Key(KeyEvent(
                keyCode = kk.scancode.toInt(),
                char = null,
                type = KeyEventType.Up,
                modifiers = mapKeyMods(kk.mod)
            ))
        }

        SDL_EVENT_WINDOW_RESIZED -> AppEvent.WindowResized

        SDL_EVENT_TEXT_INPUT -> {
            val vText = e.text.text?.toKString().orEmpty()
            if (vText.isEmpty()) null else AppEvent.TextInput(vText)
        }

        SDL_EVENT_MOUSE_WHEEL -> {
            val mw = e.wheel
            AppEvent.MouseWheel(
                x = mw.mouse_x.toInt(),
                y = mw.mouse_y.toInt(),
                deltaX = mw.x,
                deltaY = mw.y,
            )
        }

        else -> null
    }
}

private fun mapButton(b: UByte): PointerButton = when (b.toInt()) {
    1 -> PointerButton.Primary
    2 -> PointerButton.Middle
    3 -> PointerButton.Secondary
    else -> PointerButton.Primary
}

private fun mapKeyMods(mod: UShort): KeyModifiers = KeyModifiers(
    shift = (mod.toInt() and SDL_KMOD_SHIFT.toInt()) != 0,
    ctrl  = (mod.toInt() and SDL_KMOD_CTRL.toInt()) != 0,
    alt   = (mod.toInt() and SDL_KMOD_ALT.toInt()) != 0,
    meta  = (mod.toInt() and SDL_KMOD_GUI.toInt()) != 0,
)
