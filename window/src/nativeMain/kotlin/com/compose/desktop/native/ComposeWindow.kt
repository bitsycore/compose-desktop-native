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
import com.compose.desktop.native.scroll.ScrollAnimator
import com.compose.desktop.native.node.ComposeRootHost
import androidx.compose.ui.res.currentImageLoader
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

    // Install the Main dispatcher BEFORE creating the owner: ComposeOwner captures
    // Dispatchers.Main eagerly for its per-node coroutine scopes (the pointerInput /
    // gesture handlers behind upstream clickable/hoverable). If it's installed after,
    // the owner captures the MissingMainDispatcher and every gesture coroutine crashes.
    val mainDispatcher = Sdl3MainDispatcher()
    @OptIn(ExperimentalCoroutinesApi::class)
    Dispatchers.setMain(mainDispatcher)

    // Upstream layout root + owner, hidden behind the public facade.
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
                // Real focus: the ComposeOwner's FocusOwner (a FocusManager) via the host.
                androidx.compose.ui.focus.LocalFocusManager provides host.focusManager,
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
                        if (event.event.type == PointerEventType.Press) {
                            popupHost.notifyOutsidePress(event.event.x, event.event.y)
                        }
                        host.onPointer(event.event.x.toFloat(), event.event.y.toFloat(), vType, vBtn)
                        // Also drive the upstream PointerInputEventProcessor (hover / gestures /
                        // clickable via PointerInputModifierNode). Coexists with the B6a project-node
                        // dispatch above during the interaction migration.
                        host.onPointerRaw(event.event.x.toFloat(), event.event.y.toFloat(), vType, vBtn, SDL_GetTicks().toLong())
                    }
                    is AppEvent.MouseWheel -> {
                        host.onWheel(event.x.toFloat(), event.y.toFloat(), event.deltaX, event.deltaY)
                    }
                    // B6b — route keyboard + typed text to the focused node via the FocusOwner.
                    is AppEvent.Key -> host.dispatchKeyEvent(event.event)
                    is AppEvent.TextInput -> host.dispatchTextInput(event.text)
                    else -> { /* Quit handled above */ }
                }
            }

            mainDispatcher.drainPending()
            ScrollAnimator.tick()
            com.compose.desktop.native.text.currentViewportHeight = backend.windowHeight
            com.compose.desktop.native.text.currentViewportWidth = backend.windowWidth

            // ============
            //  Signal frame to recomposer
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame()
            yield()

            // ============
            //  Layout — via the upstream MeasureAndLayoutDelegate.
            backend.updateWindowSize()
            renderBackend.ensureSize(backend.pixelWidth, backend.pixelHeight)
            host.setConstraints(backend.windowWidth, backend.windowHeight)
            host.measureAndLayout()

            // ============
            //  Draw — upstream coordinator/DrawModifierNode pipeline → Canvas backend.
            renderBackend.beginFrame(backend.pixelDensity)
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
