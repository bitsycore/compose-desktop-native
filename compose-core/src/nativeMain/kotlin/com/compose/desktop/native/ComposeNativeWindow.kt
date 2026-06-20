package com.compose.desktop.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import sdl3.SDL_GetRendererName
import sdl3.SDL_MaximizeWindow
import sdl3.SDL_MinimizeWindow
import sdl3.SDL_RaiseWindow
import sdl3.SDL_RestoreWindow
import sdl3.SDL_SetWindowFullscreen
import sdl3.SDL_SetWindowSize
import sdl3.SDL_SetWindowTitle

// ==================
// MARK: ComposeNativeWindow
// ==================

/* Reactive handle on the SDL3 window backing this composition.
   composeWindow's content lambda gets one via the ComposeWindowScope
   receiver and the LocalComposeNativeWindow CompositionLocal, so deep
   composables can read state (width, title, …) and act on the window
   (minimize, close, retitle, …) without threading the instance.

   All `val`s are Compose-snapshot-backed mutableStateOf, so reading
   them subscribes the caller to recomposition. The setter methods
   call into SDL and also push the new value into the snapshot. */
class ComposeNativeWindow constructor(
    private val backend: SDL3Backend,
    val gpuMode: GpuMode,
    initialTitle: String,
) {
    private var fWidth by mutableStateOf(backend.windowWidth)
    private var fHeight by mutableStateOf(backend.windowHeight)
    private var fPixelWidth by mutableStateOf(backend.pixelWidth)
    private var fPixelHeight by mutableStateOf(backend.pixelHeight)
    private var fTitle by mutableStateOf(initialTitle)
    private var fMinimized by mutableStateOf(false)
    private var fMaximized by mutableStateOf(false)
    private var fFullscreen by mutableStateOf(false)
    private var fCloseRequested by mutableStateOf(false)

    // ============
    //  State

    /* Logical (point) width — the same units layout uses. */
    val width: Int get() = fWidth
    val height: Int get() = fHeight
    /* Physical (pixel) back-buffer size; on Retina this is `width * pixelDensity`. */
    val pixelWidth: Int get() = fPixelWidth
    val pixelHeight: Int get() = fPixelHeight
    val title: String get() = fTitle
    val isMinimized: Boolean get() = fMinimized
    val isMaximized: Boolean get() = fMaximized
    val isFullscreen: Boolean get() = fFullscreen
    val pixelDensity: Float get() = backend.pixelDensity

    /* Human-readable name of the rendering pipeline:
        "Skia / Metal", "Skia / OpenGL", "Skia / CPU"
        "SDL3 / metal", "SDL3 / opengl", "SDL3 / direct3d11", …
       For SDL3 we ask the live SDL_Renderer what driver it actually
       picked (Sdl3.Auto can resolve to different drivers per platform). */
    val rendererName: String
        get() = when (val vMode = gpuMode) {
            is GpuMode.Skia.Metal  -> "Skia / Metal"
            is GpuMode.Skia.OpenGL -> "Skia / OpenGL"
            is GpuMode.None        -> "Skia / CPU raster"
            is GpuMode.Sdl3 -> {
                val vDriver = backend.renderer?.let {
                    SDL_GetRendererName(it.reinterpret())?.toKString()
                } ?: vMode.driverHint ?: "auto"
                "SDL3 / $vDriver"
            }
            is GpuMode.Auto -> "Auto (unresolved)"
        }

    // ============
    //  Actions

    fun setTitle(inTitle: String) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowTitle(vWindow, inTitle)
        fTitle = inTitle
    }

    /* Logical size in points — SDL fires AppEvent.WindowResized which
       updates the size getters via onResized(). */
    fun setSize(inWidth: Int, inHeight: Int) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowSize(vWindow, inWidth, inHeight)
    }

    fun minimize() {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_MinimizeWindow(vWindow); fMinimized = true
    }

    fun maximize() {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_MaximizeWindow(vWindow); fMaximized = true; fMinimized = false
    }

    fun restore() {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_RestoreWindow(vWindow); fMaximized = false; fMinimized = false
    }

    fun setFullscreen(inFullscreen: Boolean) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowFullscreen(vWindow, inFullscreen); fFullscreen = inFullscreen
    }

    fun toggleFullscreen() = setFullscreen(!fFullscreen)

    fun raise() {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_RaiseWindow(vWindow)
    }

    /* Asks composeWindow's main loop to break out at the next frame.
       Same effect as the user closing the window via the OS. */
    fun close() { fCloseRequested = true }

    // ============
    //  Framework hooks — driven by composeWindow's main loop (which now lives
    //  in the compose-desktop-native module, so these are public rather than
    //  internal). Not intended for app code.

    fun onResized() {
        fWidth = backend.windowWidth
        fHeight = backend.windowHeight
        fPixelWidth = backend.pixelWidth
        fPixelHeight = backend.pixelHeight
    }

    val isCloseRequested: Boolean get() = fCloseRequested
}

// ==================
// MARK: Scope + CompositionLocal
// ==================

/* Receiver scope handed to composeWindow's content lambda so the root
   composable can write `window.setTitle(...)` directly. Deep children
   pull the same instance via LocalComposeNativeWindow.current. */
interface ComposeWindowScope {
    val window: ComposeNativeWindow
}

val LocalComposeNativeWindow = staticCompositionLocalOf<ComposeNativeWindow> {
    error("No ComposeNativeWindow in scope — wrap your composable with composeWindow { ... }")
}
