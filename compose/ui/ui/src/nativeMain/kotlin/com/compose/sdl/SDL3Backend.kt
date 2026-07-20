package com.compose.sdl

import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3Backend
// ==================

class SDL3Backend(
    private val title: String = "ComposeNativeSDL3",
    private val width: Int = 800,
    private val height: Int = 600,
    val gpuMode: GpuMode = GpuMode.Software,
    // Resource paths (inside data.kres) of the pre-decoded .rgba icon blobs.
    // The first-listed size is irrelevant — the largest becomes the base and
    // the rest become alternate resolutions. Dark set falls back to light.
    private val iconLightResourcePaths: List<String> = emptyList(),
    private val iconDarkResourcePaths: List<String> = emptyList(),
) {
    init {
        require(gpuMode !is GpuMode.Auto) {
            "SDL3Backend received GpuMode.Auto — resolve via preferredGpuMode() first"
        }
    }

    // The icon set currently applied ("light"/"dark" + paths), so a redundant
    // re-apply (e.g. a spurious theme-changed event) is skipped.
    private var appliedIconKey: String? = null

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
    // True when the active renderer paces itself to the display (vsync), so the
    // main loop can skip its manual frame delay. Set for the SDL renderer path.
    var vsyncEnabled: Boolean = false; private set

    fun init(): Boolean {
        if (!SDL_Init(SDL_INIT_VIDEO)) {
            println("SDL_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }

        // Deliver the mouse click that GIVES the window focus instead of
        // swallowing it (SDL's default eats it — the first click on an
        // unfocused window would only activate it, and the button under the
        // cursor would never fire). Desktop apps expect click-through.
        SDL_SetHint("SDL_MOUSE_FOCUS_CLICKTHROUGH", "1")

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
            is GpuMode.Software        -> 0UL
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
            is GpuMode.Software, is GpuMode.Sdl3 -> {
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
                // Pace presentation to the display — smooth + tear-free, and lets
                // the main loop drop its manual SDL_Delay. Stays false if the
                // driver can't vsync (e.g. the software renderer), so the loop
                // keeps its fallback frame cap.
                vsyncEnabled = SDL_SetRenderVSync(renderer?.reinterpret(), 1)
            }
            is GpuMode.Auto -> error("unreachable")
        }

        SDL_StartTextInput(window?.reinterpret())

        // Window / taskbar icon, matched to the current OS theme.
        applyThemeIcon()

        return true
    }

    /* Applies the theme-appropriate window icon via SDL_SetWindowIcon: the dark
       set when the OS is in dark mode and a dark set was supplied, otherwise the
       light set. No-op when no icon paths were configured or the current choice
       is already applied — so it is safe to call at init and again on every
       SDL_EVENT_SYSTEM_THEME_CHANGED. */
    fun applyThemeIcon() {
        val vWindow = window ?: return
        val vDark = systemPrefersDarkTheme() && iconDarkResourcePaths.isNotEmpty()
        val vPaths = if (vDark) iconDarkResourcePaths else iconLightResourcePaths
        if (vPaths.isEmpty()) return
        val vKey = (if (vDark) "dark" else "light") + ":" + vPaths.joinToString(",")
        if (vKey == appliedIconKey) return

        val vSurfaces = vPaths.mapNotNull { vPath ->
            loadComposeResourceBytes(vPath)?.let { iconSurfaceFromRgbaBlob(it) }
        }
        if (vSurfaces.isEmpty()) return

        // Largest surface is the base; the rest become alternate resolutions SDL
        // picks from per requested size (title bar vs taskbar vs Alt-Tab).
        val vBase = vSurfaces.maxByOrNull { it.pointed.w } ?: vSurfaces.first()
        for (vSurf in vSurfaces) {
            if (vSurf != vBase) {
                // AddSurfaceAlternateImage takes its OWN reference, so drop ours
                // immediately — the base keeps the alternate alive until it (and
                // thus the icon SDL copied) is destroyed.
                SDL_AddSurfaceAlternateImage(vBase, vSurf)
                SDL_DestroySurface(vSurf)
            }
        }
        SDL_SetWindowIcon(vWindow.reinterpret(), vBase)
        SDL_DestroySurface(vBase)
        appliedIconKey = vKey
    }

    /* Stable SDL identifier of this backend's window — events carry it, so the
       multi-window loop can route them to the right instance. 0 before init. */
    val windowId: UInt
        get() = window?.let { SDL_GetWindowID(it.reinterpret()) } ?: 0u

    /* Tears down this backend's window + renderer. inQuitSdl controls the
       process-wide SDL_Quit(): a multi-window app destroys each window with
       false and calls SDL_Quit once (via the app runtime) after the last —
       SDL_Quit shuts down ALL subsystems regardless of other live windows. */
    fun destroy(inQuitSdl: Boolean = true) {
        glContext?.let { SDL_GL_DestroyContext(it.reinterpret()) }
        metalView?.let { SDL_Metal_DestroyView(it.reinterpret()) }
        renderer?.let { SDL_DestroyRenderer(it.reinterpret()) }
        window?.let { SDL_DestroyWindow(it.reinterpret()) }
        if (inQuitSdl) SDL_Quit()
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
