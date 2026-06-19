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

        return true
    }

    fun destroy() {
        renderer?.let { SDL_DestroyRenderer(it.reinterpret()) }
        window?.let { SDL_DestroyWindow(it.reinterpret()) }
        SDL_Quit()
        renderer = null
        window = null
    }

    // ============
    //  Frame helpers

    fun beginFrame() {
        val r = renderer ?: return
        SDL_SetRenderDrawColor(r.reinterpret(), 0u, 0u, 0u, 255u)
        SDL_RenderClear(r.reinterpret())
    }

    fun endFrame() {
        val r = renderer ?: return
        SDL_RenderPresent(r.reinterpret())
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
