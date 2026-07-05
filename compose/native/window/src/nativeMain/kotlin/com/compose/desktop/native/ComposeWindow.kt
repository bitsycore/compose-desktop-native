package com.compose.desktop.native

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import com.compose.desktop.native.node.ComposeRootHost
import com.compose.desktop.native.res.currentImageLoader
import com.compose.desktop.native.text.currentTextMeasurer
import com.compose.desktop.native.window.LocalPopupHost
import com.compose.desktop.native.window.PopupLayer
import com.compose.desktop.native.window.createPopupHostState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.cinterop.reinterpret
import sdl3.SDL_Delay
import sdl3.SDL_GetTicks
import sdl3.SDL_SetWindowTitle

// ==================
// MARK: ComposeWindow
// ==================

/*
 Phase 9 B4/B5/B6a — the main loop drives the vendored upstream layout engine
 through [ComposeRootHost]: composition builds an upstream `LayoutNode` tree (via
 `ComposeUiNode.Constructor` → LayoutNode), `ComposeOwner` measures/places it via
 `MeasureAndLayoutDelegate`, and the backend paints it with `drawRoot` (→ Sdl3Canvas).

 Pointer/hover/wheel input flows through `host.onPointer` / `host.onWheel` — hit-test
 walks the upstream tree via `node.coordinates.positionInRoot()` and dispatches to
 the project input Modifier.Nodes (Clickable/Hoverable/…).

 Key events / IME text input / focus (B6b) still route through the LocalFocusManager
 stub — no-op until the upstream FocusOwner engine lands.
*/
fun nativeComposeWindow(
    title: String = "ComposeNativeSDL3",
    width: Int = 800,
    height: Int = 600,
    gpu: GpuMode = GpuMode.Auto,
    onFrame: ((backend: RenderBackend, frameIndex: Int) -> Boolean)? = null,
    content: @Composable ComposeWindowScope.() -> Unit
) {
    val gpuMode = if (gpu is GpuMode.Auto) rendererPreferredGpuMode() else gpu
    val backend = SDL3Backend(title, width, height, gpuMode = gpuMode)
    if (!backend.init()) {
        println("Failed to init SDL3 backend")
        return
    }
    backend.updateWindowSize()

    val renderBackend = createRenderBackend(backend, gpuMode)
    if (renderBackend == null || !renderBackend.ensureSize(backend.pixelWidth, backend.pixelHeight)) {
        println("Failed to init render backend for $gpuMode")
        backend.destroy()
        return
    }

    currentTextMeasurer = renderBackend.textMeasurer
    currentImageLoader = renderBackend.imageLoader

    // Project SDL3 UriHandler — LocalUriHandler.current inside vendored
    // TextLinkScope reads this. Routes to SDL_OpenURL — opens browser / mail
    // client / file manager on macOS, Linux, and Windows.
    val sdlUriHandler = object : androidx.compose.ui.platform.UriHandler {
        override fun openUri(uri: String) { openUrl(uri) }
    }

    // Install the Main dispatcher BEFORE creating the owner: ComposeOwner captures
    // Dispatchers.Main eagerly for its per-node coroutine scopes (the pointerInput /
    // gesture handlers behind upstream clickable/hoverable). If it's installed after,
    // the owner captures the MissingMainDispatcher and every gesture coroutine crashes.
    val mainDispatcher = Sdl3MainDispatcher()
    @OptIn(ExperimentalCoroutinesApi::class)
    Dispatchers.setMain(mainDispatcher)

    // Upstream layout root + owner, hidden behind the public facade.
    //
    // We follow upstream Compose Multiplatform's flow: layout runs in *physical
    // pixels*, with `LocalDensity` = the real DPR (so `8.dp.toPx()` on Retina
    // is 16 px, matching Material spec sizes). The renderer therefore does NOT
    // apply a second `canvas.scale(dpr)` / `SDL_SetRenderScale(dpr)` — that
    // would be a double-scale. Constraints below are in `pixelWidth/pixelHeight`,
    // beginFrame gets `1f`, and pointer coords are multiplied by DPR before
    // dispatch (SDL3 delivers mouse in logical points on Retina by default).
    val host = ComposeRootHost(inDensity = backend.pixelDensity)
    host.attach()

    val frameClock = SDL3FrameClock()

    val composeWindow = ComposeNativeWindow(backend, gpuMode, title)
    val windowScope = object : ComposeWindowScope {
        override val window: ComposeNativeWindow = composeWindow
    }

    runBlocking(frameClock) {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(host.applier, recomposer)

        val recomposeJob = launch { recomposer.runRecomposeAndApplyChanges() }

        val snapshotHandle = Snapshot.registerGlobalWriteObserver {
            Snapshot.sendApplyNotifications()
        }

        val popupHost = createPopupHostState()

        composition.setContent {
            CompositionLocalProvider(
                LocalComposeNativeWindow provides composeWindow,
                LocalPopupHost provides popupHost,
                // Upstream vendored CompositionLocals.kt declares each of these as
                // `staticCompositionLocalOf<T> { noLocalProvidedFor("…") }` — reading
                // one without a Provider throws. Seed them all here from the ComposeOwner
                // (density / focus / graphics / input / text / clipboard / …) so any
                // vendored composable reading them off a window succeeds.
                androidx.compose.ui.platform.LocalDensity provides host.density,
                androidx.compose.ui.platform.LocalLayoutDirection provides host.layoutDirection,
                androidx.compose.ui.platform.LocalFocusManager provides host.focusManager,
                androidx.compose.ui.platform.LocalGraphicsContext provides host.graphicsContext,
                androidx.compose.ui.platform.LocalViewConfiguration provides host.viewConfiguration,
                androidx.compose.ui.platform.LocalInputModeManager provides host.inputModeManager,
                androidx.compose.ui.platform.LocalHapticFeedback provides host.hapticFeedback,
                androidx.compose.ui.platform.LocalTextToolbar provides host.textToolbar,
                androidx.compose.ui.platform.LocalWindowInfo provides host.windowInfo,
                androidx.compose.ui.platform.LocalSoftwareKeyboardController provides host.softwareKeyboardController,
                @Suppress("DEPRECATION")
                androidx.compose.ui.platform.LocalTextInputService provides host.textInputService,
                @Suppress("DEPRECATION")
                androidx.compose.ui.platform.LocalClipboardManager provides
                    androidx.compose.ui.platform.defaultClipboardManager,
                androidx.compose.ui.platform.LocalClipboard provides
                    androidx.compose.ui.platform.defaultClipboard,
                androidx.compose.ui.platform.LocalFontFamilyResolver provides
                    com.compose.desktop.native.text.font.projectFontFamilyResolver,
                androidx.compose.ui.platform.LocalUriHandler provides sdlUriHandler,
                androidx.compose.ui.platform.LocalAutofillTree provides host.autofillTree,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    with(windowScope) { content() }
                    PopupLayer(popupHost)
                }
            }
        }

        // ============
        //  Main loop
        var running = true
        var frameIndex = 0
        var vFpsFrames = 0
        var vFpsLastMs = SDL_GetTicks()

        // Last observed mouse position (pixel space, same as host.onPointerRaw). Used to
        // re-dispatch a synthetic Move each frame so hover state refreshes after scroll /
        // relayout — otherwise items that moved out from under the mouse stay "hovered"
        // and items now under the mouse never enter hover. Upstream skiko does the
        // equivalent via SyntheticEventSender.updatePointerPosition().
        var vLastMouseX = -1f
        var vLastMouseY = -1f
        var vHasMousePos = false

        while (running) {
            Snapshot.sendApplyNotifications()

            if (composeWindow.isCloseRequested) running = false

            // ============
            //  Events — only window lifecycle for now (input rebuilt in B6).
            val events = pollEvents()
            for (event in events) {
                when (event) {
                    is AppEvent.Quit -> if (composeWindow.requestCloseFromUser()) running = false
                    is AppEvent.WindowResized -> {
                        backend.updateWindowSize()
                        composeWindow.onResized()
                    }
                    is AppEvent.Pointer -> {
                        val vType = when (event.event.type) {
                            PointerEventType.Press -> 1
                            PointerEventType.Release -> 2
                            else -> 0
                        }
                        val vBtn = when (event.event.button) {
                            PointerButton.Secondary -> 1
                            PointerButton.Tertiary -> 2
                            else -> 0
                        }
                        // SDL3 delivers mouse coords in logical points on HiDPI (Retina) —
                        // multiply by DPR so hit-testing lands in the same pixel space the
                        // layout tree is measured in.
                        val vDpr = backend.pixelDensity
                        val vPx = event.event.x.toFloat() * vDpr
                        val vPy = event.event.y.toFloat() * vDpr
                        if (event.event.type == PointerEventType.Press) {
                            popupHost.notifyOutsidePress(vPx.toInt(), vPy.toInt())
                        }
                        host.onPointer(vPx, vPy, vType, vBtn)
                        // Also drive the upstream PointerInputEventProcessor (hover / gestures /
                        // clickable via PointerInputModifierNode). Coexists with the B6a project-node
                        // dispatch above during the interaction migration.
                        host.onPointerRaw(vPx, vPy, vType, vBtn, SDL_GetTicks().toLong())
                        vLastMouseX = vPx
                        vLastMouseY = vPy
                        vHasMousePos = true
                    }
                    is AppEvent.MouseWheel -> {
                        val vDpr = backend.pixelDensity
                        host.onWheel(event.x.toFloat() * vDpr, event.y.toFloat() * vDpr, event.deltaX, event.deltaY, SDL_GetTicks().toLong())
                    }
                    // B6b — route keyboard + typed text to the focused node via the FocusOwner.
                    is AppEvent.Key -> host.dispatchKeyEvent(event.event)
                    is AppEvent.TextInput -> host.dispatchTextInput(event.text)
                    else -> { /* Quit handled above */ }
                }
            }

            // Advance node-level animations (scroll fling / animateScrollToItem) then drain the
            // resumed continuations this same frame.
            host.sendAnimationFrame(SDL_GetTicks().toLong() * 1_000_000L)
            mainDispatcher.drainPending()
            com.compose.desktop.native.text.currentViewportHeight = backend.pixelHeight
            com.compose.desktop.native.text.currentViewportWidth = backend.pixelWidth

            // ============
            //  Signal frame to recomposer
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame()
            yield()

            // ============
            //  Layout — via the upstream MeasureAndLayoutDelegate.
            backend.updateWindowSize()
            renderBackend.ensureSize(backend.pixelWidth, backend.pixelHeight)
            // Constraints in physical pixels — LocalDensity inside the tree = DPR,
            // so a `Modifier.size(64.dp)` inside a `800×600` logical window on
            // Retina resolves to 128px within a 1600×1200 pixel constraint.
            host.setConstraints(backend.pixelWidth, backend.pixelHeight)
            host.measureAndLayout()

            // Refresh hover after layout — re-dispatch the last mouse position as a
            // synthetic Move so items that scrolled out from under the cursor Exit
            // and items now under the cursor Enter. Upstream skiko does this via
            // SyntheticEventSender.updatePointerPosition() from ComposeSceneInputHandler.
            // The cost is one extra hit-test per frame when the mouse is inside the
            // window; the tracker's Enter/Exit synthesis is idempotent so re-sends
            // with an unchanged position are essentially free.
            if (vHasMousePos) {
                host.onPointerRaw(vLastMouseX, vLastMouseY, 0, 0, SDL_GetTicks().toLong())
            }

            // ============
            //  Draw — upstream coordinator/DrawModifierNode pipeline → Canvas backend.
            //  Renderer runs 1:1 (no `canvas.scale(dpr)` / `SDL_SetRenderScale(dpr)`):
            //  the tree already measured itself in physical pixels above.
            renderBackend.beginFrame(1f)
            renderBackend.drawRoot(host)

            if (onFrame != null && !onFrame(renderBackend, frameIndex)) {
                running = false
            }
            renderBackend.endFrame()
            frameIndex++

            // ============
            //  FPS
            vFpsFrames++
            val vNowMs = SDL_GetTicks()
            val vElapsed = (vNowMs - vFpsLastMs).toInt()
            if (vElapsed >= 1000) {
                val vFps = vFpsFrames * 1000 / vElapsed
                composeWindow.updateFps(vFps)
                SDL_SetWindowTitle(backend.window?.reinterpret(), "${composeWindow.title} · $vFps FPS")
                vFpsFrames = 0
                vFpsLastMs = vNowMs
            }

            SDL_Delay(if (backend.vsyncEnabled) 1u else 16u)
        }

        snapshotHandle.dispose()
        composition.dispose()
        recomposer.cancel()
        recomposeJob.cancelAndJoin()
    }

    mainDispatcher.close()
    @OptIn(ExperimentalCoroutinesApi::class)
    Dispatchers.resetMain()

    renderBackend.destroy()
    backend.destroy()
}
