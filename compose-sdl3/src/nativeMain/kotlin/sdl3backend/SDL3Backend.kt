package sdl3backend

import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3Backend
// ==================

class SDL3Backend(
    private val title: String = "ComposeNativeSDL3",
    private val width: Int = 800,
    private val height: Int = 600,
    private val useGpu: Boolean = false,
) {
    var window: COpaquePointer? = null; private set
    var renderer: COpaquePointer? = null; private set
    var glContext: COpaquePointer? = null; private set
    var windowWidth: Int = width; private set
    var windowHeight: Int = height; private set

    fun init(): Boolean {
        if (!SDL_Init(SDL_INIT_VIDEO)) {
            println("SDL_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        if (useGpu) {
            // Request a core profile suitable for Skia's GL backend. Skia is
            // happy with GL 3.2+ core on macOS / Linux.
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_CONTEXT_MAJOR_VERSION, 3)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_CONTEXT_MINOR_VERSION, 2)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_CONTEXT_PROFILE_MASK,
                SDL_GL_CONTEXT_PROFILE_CORE.toInt())
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_DOUBLEBUFFER, 1)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_STENCIL_SIZE, 8)
        }

        val flags = SDL_WINDOW_RESIZABLE or (if (useGpu) SDL_WINDOW_OPENGL else 0UL)
        window = SDL_CreateWindow(title, width, height, flags)
        if (window == null) {
            println("SDL_CreateWindow failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        if (useGpu) {
            glContext = SDL_GL_CreateContext(window?.reinterpret())
            if (glContext == null) {
                println("SDL_GL_CreateContext failed: ${SDL_GetError()?.toKString()}")
                return false
            }
            SDL_GL_MakeCurrent(window?.reinterpret(), glContext?.reinterpret())
            SDL_GL_SetSwapInterval(1)  // vsync
        } else {
            renderer = SDL_CreateRenderer(window?.reinterpret(), null)
            if (renderer == null) {
                println("SDL_CreateRenderer failed: ${SDL_GetError()?.toKString()}")
                return false
            }
            SDL_SetRenderDrawBlendMode(renderer?.reinterpret(), SDL_BLENDMODE_BLEND)
        }

        // Enable text input so SDL emits SDL_EVENT_TEXT_INPUT. Until we have
        // per-focus IME management (PLAN.md Phase 7) we keep it on globally.
        SDL_StartTextInput(window?.reinterpret())

        return true
    }

    fun destroy() {
        glContext?.let { SDL_GL_DestroyContext(it.reinterpret()) }
        renderer?.let { SDL_DestroyRenderer(it.reinterpret()) }
        window?.let { SDL_DestroyWindow(it.reinterpret()) }
        SDL_Quit()
        glContext = null
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
