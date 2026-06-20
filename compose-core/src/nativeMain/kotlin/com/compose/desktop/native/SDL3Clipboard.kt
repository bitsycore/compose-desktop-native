package com.compose.desktop.native

import androidx.compose.ui.platform.Clipboard
import kotlinx.cinterop.toKString
import sdl3.SDL_GetClipboardText
import sdl3.SDL_HasClipboardText
import sdl3.SDL_SetClipboardText
import sdl3.SDL_free

// ==================
// MARK: SDL3Clipboard
// ==================

/* SDL3-backed Clipboard. The returned char* from SDL_GetClipboardText is
   heap-allocated and must be freed with SDL_free — see SDL3 docs. */
class SDL3Clipboard : Clipboard {
    override fun getText(): String? {
        if (!SDL_HasClipboardText()) return null
        val vRaw = SDL_GetClipboardText() ?: return null
        val vText = vRaw.toKString()
        SDL_free(vRaw)
        return vText.ifEmpty { null }
    }

    override fun setText(inText: String) {
        SDL_SetClipboardText(inText)
    }
}
