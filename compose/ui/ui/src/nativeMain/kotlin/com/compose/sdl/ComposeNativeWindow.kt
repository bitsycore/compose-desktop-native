package com.compose.sdl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import sdl3.SDL_GetRendererName
import sdl3.SDL_MaximizeWindow
import sdl3.SDL_MinimizeWindow
import sdl3.SDL_RaiseWindow
import sdl3.SDL_RestoreWindow
import sdl3.SDL_SetWindowAlwaysOnTop
import sdl3.SDL_SetWindowBordered
import sdl3.SDL_SetWindowFocusable
import sdl3.SDL_SetWindowFullscreen
import sdl3.SDL_SetWindowOpacity
import sdl3.SDL_SetWindowResizable
import sdl3.SDL_SetWindowSize
import sdl3.SDL_SetWindowTitle

// ==================
// MARK: ComposeNativeWindow
// ==================

/** Reactive handle on the SDL3 window backing this composition.
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
    private var fFps by mutableStateOf(0)

    // ============
    //  State

    /** Logical (point) width - the same units layout uses. */
    val width: Int get() = fWidth
    val height: Int get() = fHeight
    /** Physical (pixel) back-buffer size; on Retina this is `width * pixelDensity`. */
    val pixelWidth: Int get() = fPixelWidth
    val pixelHeight: Int get() = fPixelHeight
    val title: String get() = fTitle
    val isMinimized: Boolean get() = fMinimized
    val isMaximized: Boolean get() = fMaximized
    val isFullscreen: Boolean get() = fFullscreen
    val pixelDensity: Float get() = backend.pixelDensity
    /** Frames per second, refreshed ~once a second by the render loop. Snapshot-
       backed, so reading it in a composable recomposes when it changes. */
    val fps: Int get() = fFps

    /** Human-readable name of the rendering pipeline:
        "Skia / Metal", "Skia / OpenGL", "Skia / CPU raster". */
    val rendererName: String
        get() = when (gpuMode) {
            is GpuMode.Skia.Metal  -> "Skia / Metal"
            is GpuMode.Skia.OpenGL -> "Skia / OpenGL"
            is GpuMode.Software        -> "Skia / CPU raster"
            is GpuMode.Auto -> "Auto (unresolved)"
        }

    // ============
    //  Actions

    fun setTitle(inTitle: String) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowTitle(vWindow, inTitle)
        fTitle = inTitle
    }

    /** Logical size in points - SDL fires AppEvent.WindowResized which
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

    // ============
    //  Mutable window attributes (Compose Desktop's Window() parameters)
    //
    //  Window() re-applies these from the composition whenever they change, so
    //  app code normally passes them as parameters rather than calling these.
    //  `transparent` is absent on purpose: SDL needs SDL_WINDOW_TRANSPARENT at
    //  creation and offers no setter, so it is a construction-time attribute.

    private var fUndecorated = false
    private var fResizable = true
    private var fAlwaysOnTop = false
    private var fFocusable = true
    private var fEnabled = true

    val isUndecorated: Boolean get() = fUndecorated
    val isResizable: Boolean get() = fResizable
    val isAlwaysOnTop: Boolean get() = fAlwaysOnTop
    val isFocusable: Boolean get() = fFocusable

    /** False hides the window's title bar and border. */
    fun setUndecorated(inUndecorated: Boolean) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowBordered(vWindow, !inUndecorated); fUndecorated = inUndecorated
    }

    fun setResizable(inResizable: Boolean) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowResizable(vWindow, inResizable); fResizable = inResizable
    }

    fun setAlwaysOnTop(inAlwaysOnTop: Boolean) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowAlwaysOnTop(vWindow, inAlwaysOnTop); fAlwaysOnTop = inAlwaysOnTop
    }

    /** False stops the window taking keyboard focus when clicked or raised. */
    fun setFocusable(inFocusable: Boolean) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowFocusable(vWindow, inFocusable); fFocusable = inFocusable
    }

    /** Window opacity, 0f (fully transparent) to 1f. Needs a compositing window
       manager; SDL reports failure on platforms without one. */
    fun setOpacity(inOpacity: Float) {
        val vWindow = backend.window?.reinterpret<cnames.structs.SDL_Window>() ?: return
        SDL_SetWindowOpacity(vWindow, inOpacity.coerceIn(0f, 1f))
    }

    /** True while the window accepts input. A disabled window still renders and
       repaints; it just drops pointer / keyboard / text / wheel / drop events,
       which is how Compose Desktop's `enabled = false` behaves. */
    val isEnabled: Boolean get() = fEnabled

    fun setEnabled(inEnabled: Boolean) { fEnabled = inEnabled }

    /** Asks composeWindow's main loop to break out at the next frame.
       Same effect as the user closing the window via the OS. Bypasses any
       onCloseRequest handler - this is the "really quit now" path. */
    fun close() { fCloseRequested = true }

    // ============
    //  Close interception

    private var fOnCloseRequest: (() -> Boolean)? = null

    /** Register a handler invoked when the user tries to close the window (OS
       close button / Quit). Return true to let the close proceed, false to veto
       it (e.g. to show an "unsaved changes" dialog first, then call close()
       once the user confirms). Pass null to clear. */
    fun setOnCloseRequest(inHandler: (() -> Boolean)?) { fOnCloseRequest = inHandler }

    // ============
    //  Global key shortcuts

    private var fOnKeyShortcut: ((KeyEvent) -> Boolean)? = null

    /** Register a handler for key events the focused node didn't consume - for
       app-wide shortcuts (Ctrl+S, etc.). Receives every unconsumed key (and all
       keys when nothing is focused). Return true if handled. Pass null to clear. */
    fun setOnKeyShortcut(inHandler: ((KeyEvent) -> Boolean)?) { fOnKeyShortcut = inHandler }

    // ============
    //  Framework hooks - driven by composeWindow's main loop (which lives in
    //  the :desktop-native-window module, so these are public rather than internal). Not
    //  intended for app code.

    fun onResized() {
        fWidth = backend.windowWidth
        fHeight = backend.windowHeight
        fPixelWidth = backend.pixelWidth
        fPixelHeight = backend.pixelHeight
    }

    /** Called by the main loop ~once a second with the measured frame rate. */
    fun updateFps(inFps: Int) { fFps = inFps }

    val isCloseRequested: Boolean get() = fCloseRequested

    /** Driven by composeWindow's main loop on an OS close / Quit event. Returns
       true if the close should proceed (no handler, or the handler allowed it). */
    fun requestCloseFromUser(): Boolean = fOnCloseRequest?.invoke() ?: true

    /** Driven by composeWindow's main loop for keys the focused node didn't
       consume. Returns true if the shortcut handler handled it. */
    fun dispatchKeyShortcut(inEvent: KeyEvent): Boolean = fOnKeyShortcut?.invoke(inEvent) ?: false

    // ============
    //  Raw SDL escape hatch

    /**
     The live SDL handles behind this window, for calling the `sdl3.*` cinterop
     directly when the Compose-level API doesn't cover something.

     Returns a snapshot: the pointers are only valid while the window is alive,
     so re-read it rather than caching, and never use it after the window closes.
     Whatever you do through it is outside the port's control - resizing,
     destroying or re-creating the window or its renderer behind Compose's back
     will desync the renderer state.
     */
    fun rawSdlHandles(): RawSdlHandles = RawSdlHandles(
        window = backend.window,
        renderer = backend.renderer,
        glContext = backend.glContext,
        metalView = backend.metalView,
    )
}

/**
 Opaque SDL pointers for one window, as handed out by
 [ComposeNativeWindow.rawSdlHandles].

 `reinterpret()` each to the `cnames.structs.*` type the SDL function expects:

     val vHandles = window.rawSdlHandles()
     vHandles.window?.let { sdl3.SDL_FlashWindow(it.reinterpret(), FLASH_BRIEFLY) }

 Which pointers are non-null depends on the resolved [GpuMode]: `renderer` is
 set only for the CPU-raster (Software) backend, `glContext` only for Skia
 OpenGL, and `metalView` only for Skia Metal. `window` is always set on a
 successfully created window.
 */
data class RawSdlHandles(
    /** `SDL_Window*` - always present while the window lives. */
    val window: COpaquePointer?,
    /** `SDL_Renderer*` - CPU-raster (Software) backend only. */
    val renderer: COpaquePointer?,
    /** `SDL_GLContext` - Skia OpenGL backend only. */
    val glContext: COpaquePointer?,
    /** `SDL_MetalView` - Skia Metal backend only. */
    val metalView: COpaquePointer?,
)

// ==================
// MARK: Scope + CompositionLocal
// ==================

/** Receiver scope handed to composeWindow's content lambda so the root
   composable can write `window.setTitle(...)` directly. Deep children
   pull the same instance via LocalComposeNativeWindow.current. */
interface ComposeWindowScope {
    val window: ComposeNativeWindow
}

val LocalComposeNativeWindow = staticCompositionLocalOf<ComposeNativeWindow> {
    error("No ComposeNativeWindow in scope - wrap your composable with composeWindow { ... }")
}
