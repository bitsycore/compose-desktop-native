package com.compose.desktop.native

import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3Backend
// ==================

class SDL3Backend(
    private val title: String = "ComposeNativeSDL3",
    private val width: Int = 800,
    private val height: Int = 600,
    val gpuMode: GpuMode = GpuMode.None,
) {
    init {
        require(gpuMode !is GpuMode.Auto) {
            "SDL3Backend received GpuMode.Auto — resolve via preferredGpuMode() first"
        }
    }

    var window: COpaquePointer? = null; private set
    var renderer: COpaquePointer? = null; private set
    var glContext: COpaquePointer? = null; private set
    var metalView: COpaquePointer? = null; private set
    // Logical (point) size — what layout / hit-testing / input events use.
    var windowWidth: Int = width; private set
    var windowHeight: Int = height; private set
    // Physical (pixel) size of the back buffer — what bridges allocate.
    // On Retina with HIGH_PIXEL_DENSITY this is 2x the logical size.
    var pixelWidth: Int = width; private set
    var pixelHeight: Int = height; private set

    fun init(): Boolean {
        if (!SDL_Init(SDL_INIT_VIDEO)) {
            println("SDL_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        if (gpuMode is GpuMode.Skia.OpenGL) {
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_CONTEXT_MAJOR_VERSION, 3)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_CONTEXT_MINOR_VERSION, 2)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_CONTEXT_PROFILE_MASK,
                SDL_GL_CONTEXT_PROFILE_CORE)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_DOUBLEBUFFER, 1)
            SDL_GL_SetAttribute(SDL_GLAttr.SDL_GL_STENCIL_SIZE, 8)
        }

        val flags = SDL_WINDOW_RESIZABLE or SDL_WINDOW_HIGH_PIXEL_DENSITY or when (gpuMode) {
            is GpuMode.Skia.OpenGL -> SDL_WINDOW_OPENGL
            is GpuMode.Skia.Metal  -> SDL_WINDOW_METAL
            is GpuMode.None        -> 0UL
            is GpuMode.Sdl3        -> 0UL
            is GpuMode.Auto        -> error("unreachable")
        }
        window = SDL_CreateWindow(title, width, height, flags)
        if (window == null) {
            println("SDL_CreateWindow failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        when (gpuMode) {
            is GpuMode.Skia.OpenGL -> {
                glContext = SDL_GL_CreateContext(window?.reinterpret())
                if (glContext == null) {
                    println("SDL_GL_CreateContext failed: ${SDL_GetError()?.toKString()}")
                    return false
                }
                SDL_GL_MakeCurrent(window?.reinterpret(), glContext?.reinterpret())
                SDL_GL_SetSwapInterval(1)
            }
            is GpuMode.Skia.Metal -> {
                metalView = SDL_Metal_CreateView(window?.reinterpret())
                if (metalView == null) {
                    println("SDL_Metal_CreateView failed: ${SDL_GetError()?.toKString()}")
                    return false
                }
            }
            is GpuMode.None, is GpuMode.Sdl3 -> {
                // For Sdl3.* with a driver pin we steer SDL_CreateRenderer
                // via SDL_HINT_RENDER_DRIVER. SDL3 looks the hint up the
                // moment the renderer is created.
                val vDriverHint = (gpuMode as? GpuMode.Sdl3)?.driverHint
                if (vDriverHint != null) {
                    SDL_SetHint("SDL_RENDER_DRIVER", vDriverHint)
                }
                renderer = SDL_CreateRenderer(window?.reinterpret(), null)
                if (renderer == null) {
                    println("SDL_CreateRenderer failed: ${SDL_GetError()?.toKString()}")
                    return false
                }
                SDL_SetRenderDrawBlendMode(renderer?.reinterpret(), SDL_BLENDMODE_BLEND)
            }
            is GpuMode.Auto -> error("unreachable")
        }

        SDL_StartTextInput(window?.reinterpret())

        return true
    }

    fun destroy() {
        glContext?.let { SDL_GL_DestroyContext(it.reinterpret()) }
        metalView?.let { SDL_Metal_DestroyView(it.reinterpret()) }
        renderer?.let { SDL_DestroyRenderer(it.reinterpret()) }
        window?.let { SDL_DestroyWindow(it.reinterpret()) }
        SDL_Quit()
        glContext = null
        metalView = null
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
            SDL_GetWindowSizeInPixels(w.reinterpret(), ww.ptr, hh.ptr)
            pixelWidth = ww.value
            pixelHeight = hh.value
        }
    }

    /* Backing-store scale: pixelWidth / windowWidth. 1.0 on standard
       displays, 2.0 on Retina with SDL_WINDOW_HIGH_PIXEL_DENSITY active.
       Used to scale the Skia canvas so Compose's logical-point layout
       maps to physical pixels. */
    val pixelDensity: Float
        get() = if (windowWidth > 0) pixelWidth.toFloat() / windowWidth.toFloat() else 1f
}
