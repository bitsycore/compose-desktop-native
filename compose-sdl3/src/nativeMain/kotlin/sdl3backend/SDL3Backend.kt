package sdl3backend

import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3Backend
// ==================

class SDL3Backend(
    private val title: String = "ComposeNativeSDL3",
    private val width: Int = 800,
    private val height: Int = 600
) {
    var window: COpaquePointer? = null; private set
    var renderer: COpaquePointer? = null; private set
    var windowWidth: Int = width; private set
    var windowHeight: Int = height; private set

    fun init(): Boolean {
        if (!SDL_Init(SDL_INIT_VIDEO)) {
            println("SDL_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        window = SDL_CreateWindow(title, width, height, SDL_WINDOW_RESIZABLE)
        if (window == null) {
            println("SDL_CreateWindow failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        renderer = SDL_CreateRenderer(window?.reinterpret(), null)
        if (renderer == null) {
            println("SDL_CreateRenderer failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        // Alpha-blend by default so transparent text linegap and translucent
        // state-layer overlays (Material hover / press) composite correctly.
        SDL_SetRenderDrawBlendMode(renderer?.reinterpret(), SDL_BLENDMODE_BLEND)

        // Enable text input so SDL emits SDL_EVENT_TEXT_INPUT. Until we have
        // per-focus IME management (PLAN.md Phase 7) we keep it on globally.
        SDL_StartTextInput(window?.reinterpret())

        return true
    }

    fun destroy() {
        renderer?.let { SDL_DestroyRenderer(it.reinterpret()) }
        window?.let { SDL_DestroyWindow(it.reinterpret()) }
        SDL_Quit()
        renderer = null
        window = null
    }

    fun updateWindowSize() {
        val w = window ?: return
        memScoped {
            val ww = alloc<IntVar>()
            val hh = alloc<IntVar>()
            SDL_GetWindowSize(w.reinterpret(), ww.ptr, hh.ptr)
            windowWidth = ww.value
            windowHeight = hh.value
        }
    }
}
