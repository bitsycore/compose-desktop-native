package com.compose.desktop.native

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.KeyEventDispatch
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnDragModifier
import androidx.compose.ui.OnPressedModifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputElement
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeApplier
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.res.currentImageLoader
import androidx.compose.ui.text.currentTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.LocalPopupHost
import androidx.compose.ui.window.PopupLayer
import androidx.compose.ui.window.createPopupHostState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import sdl3.SDL_Delay

// ==================
// MARK: ComposeWindow
// ==================

fun nativeComposeWindow(
    title: String = "ComposeNativeSDL3",
    width: Int = 800,
    height: Int = 600,
    gpu: GpuMode = GpuMode.Auto,
    onFrame: ((backend: RenderBackend, frameIndex: Int) -> Boolean)? = null,
    content: @Composable ComposeWindowScope.() -> Unit
) {
    // Resolve Auto at the call site so SDL3Backend / RenderBackend never see
    // it. preferredGpuMode() is per-target.
    val gpuMode = if (gpu is GpuMode.Auto) preferredGpuMode() else gpu
    val backend = SDL3Backend(title, width, height, gpuMode = gpuMode)
    if (!backend.init()) {
        println("Failed to init SDL3 backend")
        return
    }
    // Pull the real (HiDPI-aware) pixel dimensions before we hand them to
    // a bridge — without this they default to the logical window size and
    // Retina back buffers come out half-resolution.
    backend.updateWindowSize()

    val renderBackend = makeRenderBackend(backend, gpuMode)
    if (renderBackend == null || !renderBackend.ensureSize(backend.pixelWidth, backend.pixelHeight)) {
        println("Failed to init render backend for $gpuMode")
        backend.destroy()
        return
    }

    // Hook the renderer's measurer into the common layout pass so text
    // bounds match what's actually drawn (fixes off-centre text in
    // Buttons / Boxes).
    currentTextMeasurer = renderBackend.textMeasurer

    // Same wiring for images: the layout pass sizes Image nodes via the
    // backend's decode cache, and Res.readBytes reads bundled files.
    currentImageLoader = renderBackend.imageLoader

    // Wire SDL3 clipboard so TextField Cmd+C / Cmd+V work in commonMain.
    currentClipboard = SDL3Clipboard()

    val rootNode = LayoutNode()
    val frameClock = SDL3FrameClock()

    // Install our SDL3-driven Dispatchers.Main. Kotlin/Native ships no Main
    // dispatcher on Linux/Windows, and the Darwin one posts to GCD's main
    // queue (which our SDL_Delay loop doesn't reliably pump). The dispatcher
    // is drained once per frame from inside the main loop, so app code can
    // withContext(Dispatchers.Main) { ... } portably.
    val mainDispatcher = Sdl3MainDispatcher()
    @OptIn(ExperimentalCoroutinesApi::class)
    Dispatchers.setMain(mainDispatcher)

    // Reactive handle to the window — provided to content via both a
    // receiver scope and a CompositionLocal. Event handlers feed the
    // window state below (resize → onResized, etc.).
    val composeWindow = ComposeNativeWindow(backend, gpuMode, title)
    val windowScope = object : ComposeWindowScope {
        override val window: ComposeNativeWindow = composeWindow
    }

    runBlocking(frameClock) {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(NodeApplier(rootNode), recomposer)

        val recomposeJob = launch { recomposer.runRecomposeAndApplyChanges() }

        // Without this, mutableStateOf writes from click handlers never reach the
        // recomposer and the UI silently stops updating after the first frame.
        val snapshotHandle = Snapshot.registerGlobalWriteObserver {
            Snapshot.sendApplyNotifications()
        }

        val popupHost = createPopupHostState()
        // Lazy FocusManager — body filled in once setFocus is in scope below.
        var focusManagerImpl: androidx.compose.ui.focus.FocusManager? = null
        val focusManagerProxy = object : androidx.compose.ui.focus.FocusManager {
            override fun focusOnNode(inNode: LayoutNode) { focusManagerImpl?.focusOnNode(inNode) }
            override fun clearFocus() { focusManagerImpl?.clearFocus() }
        }
        composition.setContent {
            CompositionLocalProvider(
                LocalComposeNativeWindow provides composeWindow,
                LocalPopupHost provides popupHost,
                androidx.compose.ui.focus.LocalFocusManager provides focusManagerProxy,
            ) {
                // Root Box: main content + overlay layer as sibling. The
                // overlay is the *last* child so popups draw above and the
                // hit-tester (which iterates children in reverse) hits them
                // first. Each popup positions itself via Modifier.offset /
                // Box(contentAlignment) inside its own composable.
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

        // ============
        //  Interaction state (hover / press / click target) — keyed by
        //  LayoutNode so it survives recomposition (modifier identity does
        //  not).
        val activeHoverNodes = mutableMapOf<LayoutNode, (Boolean) -> Unit>()
        var activePressNode: LayoutNode? = null
        var activePressCallback: ((Boolean) -> Unit)? = null
        var armedClickNode: LayoutNode? = null

        // One focused node per window. onFocusChanged fires false on the old,
        // true on the new. Click outside any focusable clears focus.
        var focusedNode: LayoutNode? = null
        var focusedCallback: ((Boolean) -> Unit)? = null

        // Drag capture: on Press inside a draggable, hold the (node, modifier)
        // until Release so Move events route here regardless of where the
        // cursor wanders (matches gesture-detector semantics).
        var dragNode: LayoutNode? = null
        var dragModifier: OnDragModifier? = null

        fun setFocus(inNode: LayoutNode?, inCallback: ((Boolean) -> Unit)?) {
            if (inNode === focusedNode) return
            focusedCallback?.invoke(false)
            focusedNode = inNode
            focusedCallback = inCallback
            inCallback?.invoke(true)
        }

        /* Concrete FocusManager wired into setFocus. Walks the node's
           modifier chain to find its FocusableModifier so the focus
           callback fires; falls back to focusing-without-callback if
           the node has no focusable. */
        focusManagerImpl = object : androidx.compose.ui.focus.FocusManager {
            override fun focusOnNode(inNode: LayoutNode) {
                var vCallback: ((Boolean) -> Unit)? = null
                inNode.modifier.foldIn(Unit) { _, vEl ->
                    if (vEl is androidx.compose.ui.FocusableModifier) vCallback = vEl.onFocusChanged
                }
                setFocus(inNode, vCallback)
            }
            override fun clearFocus() = setFocus(null, null)
        }

        /* Walk the tree and pop each FocusRequester onto its hosting
           node so requestFocus() can resolve the node back. Fast and
           good enough for a tree of a few hundred nodes; we could keep
           a registry instead if it ever shows up on a profile. */
        fun bindFocusRequesters(inRoot: LayoutNode) {
            fun walk(inN: LayoutNode) {
                inN.modifier.foldIn(Unit) { _, vEl ->
                    if (vEl is androidx.compose.ui.focus.FocusRequesterModifier) {
                        vEl.focusRequester.attachedNode = inN
                        vEl.focusRequester.focusManager = focusManagerImpl
                    }
                }
                for (vC in inN.children) walk(vC)
            }
            walk(inRoot)
        }

        fun dispatchHover(inX: Int, inY: Int) {
            val vHit = rootNode.hitTest(inX, inY)
            val vNewChain = vHit?.collectHoverableChain() ?: emptyList()
            val vNewMap = vNewChain.associate { it.first to it.second.onChange }
            // Exit nodes no longer under the cursor
            for ((vNode, vCb) in activeHoverNodes) {
                if (vNode !in vNewMap) vCb(false)
            }
            // Enter nodes newly under the cursor
            for ((vNode, vCb) in vNewMap) {
                if (vNode !in activeHoverNodes) vCb(true)
            }
            activeHoverNodes.clear()
            activeHoverNodes.putAll(vNewMap)
        }

        fun cancelPress() {
            activePressCallback?.invoke(false)
            activePressNode = null
            activePressCallback = null
        }

        /* Walk the hit target → root chain. On every node that has a
           PointerInputElement modifier, deliver the change in local
           coords. Each scope's awaitPointerEvent resumes synchronously
           because the coroutine is on the same Dispatchers.Main as us. */
        fun dispatchPointerInput(inRoot: LayoutNode, inX: Int, inY: Int, inPressed: Boolean) {
            val vHit = inRoot.hitTest(inX, inY) ?: return
            var vNode: LayoutNode? = vHit
            while (vNode != null) {
                val vN = vNode
                vN.modifier.foldIn(Unit) { _, vEl ->
                    if (vEl is PointerInputElement) {
                        val vLocalX = (inX - vN.absoluteX).toFloat()
                        val vLocalY = (inY - vN.absoluteY).toFloat()
                        vEl.scope.deliverChange(Offset(vLocalX, vLocalY), inPressed, 0L)
                    }
                }
                vNode = vN.parent
            }
        }

        fun cursorInsideNode(inHit: LayoutNode?, inNode: LayoutNode): Boolean =
            generateSequence(inHit) { it.parent }.any { it === inNode }

        while (running) {
            // ============
            //  Drain any pending snapshot writes from the previous frame's handlers
            Snapshot.sendApplyNotifications()

            // A composable that called window.close() earlier — break the
            // loop the same way SDL_EVENT_QUIT would.
            if (composeWindow.isCloseRequested) running = false

            // ============
            //  Events
            val events = pollEvents()
            for (event in events) {
                when (event) {
                    is AppEvent.Quit -> if (composeWindow.requestCloseFromUser()) running = false

                    is AppEvent.WindowResized -> {
                        backend.updateWindowSize()
                        composeWindow.onResized()
                    }

                    is AppEvent.Pointer -> {
                        val vPx = event.event.x
                        val vPy = event.event.y
                        // Pointer-input modifier delivery: walk the hit-target →
                        // root chain and deliver this change to every
                        // PointerInputElement we cross. Position is converted to
                        // each owning node's local space so detectTapGestures /
                        // detectDragGestures see node-relative coords.
                        val vCurrentlyPressed = event.event.type != PointerEventType.Release
                        dispatchPointerInput(rootNode, vPx, vPy, vCurrentlyPressed)
                        when (event.event.type) {
                            PointerEventType.Move -> {
                                dispatchHover(vPx, vPy)
                                val vHit = rootNode.hitTest(vPx, vPy)
                                // Drag off the pressed node cancels press + click.
                                activePressNode?.let { vN ->
                                    if (!cursorInsideNode(vHit, vN)) cancelPress()
                                }
                                armedClickNode?.let { vN ->
                                    if (!cursorInsideNode(vHit, vN)) armedClickNode = null
                                }
                                // Captured drag: route Move events to the original
                                // drag node even if the cursor leaves its bounds.
                                val dn = dragNode
                                val dm = dragModifier
                                if (dn != null && dm != null) {
                                    dm.onDrag(vPx - dn.absoluteX, vPy - dn.absoluteY)
                                }
                            }
                            PointerEventType.Press -> {
                                val vHit = rootNode.hitTest(vPx, vPy)
                                if (event.event.button == PointerButton.Secondary) {
                                    // Right-click: fire only the context-menu handler (with
                                    // the window-coord click position); don't arm the
                                    // primary press / click / drag / focus.
                                    vHit?.findSecondaryClickHandler()?.invoke(vPx, vPy)
                                } else {
                                    cancelPress()
                                    val vPressable = vHit?.findPressable()
                                    if (vPressable != null) {
                                        activePressNode = vPressable.first
                                        activePressCallback = vPressable.second.onChange
                                        vPressable.second.onChange(true)
                                    }
                                    armedClickNode = vHit?.findClickableNode()
                                    // Update focus: click on a focusable focuses it;
                                    // click on nothing focusable clears focus.
                                    val vFocusable = vHit?.findFocusableNode()
                                    setFocus(vFocusable?.first, vFocusable?.second?.onFocusChanged)
                                    // Positional press dispatch (TextField cursor placement).
                                    // Walk from the hit-test target up, firing each
                                    // OnPressedModifier with node-relative coordinates.
                                    var pn: LayoutNode? = vHit
                                    while (pn != null) {
                                        val node = pn
                                        val relX = vPx - node.absoluteX
                                        val relY = vPy - node.absoluteY
                                        node.modifier.foldIn(Unit) { _, el ->
                                            if (el is OnPressedModifier) el.handler(relX, relY)
                                        }
                                        pn = node.parent
                                    }
                                    // Begin drag capture if the press lands on a draggable.
                                    val vDraggable = vHit?.findDraggable()
                                    if (vDraggable != null) {
                                        dragNode = vDraggable.first
                                        dragModifier = vDraggable.second
                                        val dn = vDraggable.first
                                        vDraggable.second.onStart(vPx - dn.absoluteX, vPy - dn.absoluteY)
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                cancelPress()
                                // End any in-progress drag, regardless of where the release lands.
                                dragModifier?.onEnd?.invoke()
                                dragNode = null
                                dragModifier = null
                                val vHit = rootNode.hitTest(vPx, vPy)
                                val vArmed = armedClickNode
                                armedClickNode = null
                                if (vArmed != null && cursorInsideNode(vHit, vArmed)) {
                                    vArmed.findClickHandler()?.invoke()
                                }
                            }
                        }
                    }

                    is AppEvent.Key -> {
                        val vConsumed = focusedNode?.dispatchKeyEvent(KeyEventDispatch(event.event)) ?: false
                        if (!vConsumed) composeWindow.dispatchKeyShortcut(event.event)
                    }

                    is AppEvent.TextInput -> {
                        focusedNode?.dispatchTextInput(event.text)
                    }

                    is AppEvent.MouseWheel -> {
                        val vHit = rootNode.hitTest(event.x, event.y)
                        // SDL wheel: positive y = scrolled up (away from user) →
                        // content should scroll up → state.value decreases.
                        // Convert wheel "lines" to pixels (~50px/line).
                        val vScrollY = vHit?.findVerticalScrollAncestor()
                        val vScrollX = vHit?.findHorizontalScrollAncestor()
                        if (vScrollY != null && event.deltaY != 0f) {
                            vScrollY.scrollBy(-(event.deltaY * 50f).toInt())
                        } else if (vScrollX != null && event.deltaY != 0f) {
                            // No vertical scroller under the cursor but a horizontal one is
                            // (e.g. the tab strip) — let the wheel scroll it sideways.
                            vScrollX.scrollBy(-(event.deltaY * 50f).toInt())
                        }
                        if (vScrollX != null && event.deltaX != 0f) {
                            vScrollX.scrollBy(-(event.deltaX * 50f).toInt())
                        }
                    }
                }
            }

            // ============
            //  Drain Dispatchers.Main posts from the previous frame's
            //  withContext / launch callbacks BEFORE notifying the
            //  recomposer, so any state writes they perform land in this
            //  frame's composition rather than the next one.
            mainDispatcher.drainPending()

            // ============
            //  Signal frame to recomposer
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame()
            yield()

            // ============
            //  Layout — also resize the back buffer if the window changed.
            backend.updateWindowSize()
            renderBackend.ensureSize(backend.pixelWidth, backend.pixelHeight)
            val constraints = Constraints.fixed(backend.windowWidth, backend.windowHeight)
            rootNode.measure(constraints)
            rootNode.place(0, 0)
            // Rebind FocusRequesters every frame so a state-driven
            // recomposition that swaps which node carries the modifier
            // picks up the new binding by the time the next requestFocus
            // call lands.
            bindFocusRequesters(rootNode)
            // Fire onGloballyPositioned callbacks now that absolute coords
            // are valid. Popups (DropdownMenu/Tooltip) read these to anchor
            // to their target's current window-coordinate position.
            rootNode.dispatchGloballyPositioned()

            // ============
            //  Draw — the backend scales by DPR so the logical-point layout
            //  maps to physical pixels on HiDPI displays.
            renderBackend.beginFrame(backend.pixelDensity)
            renderBackend.draw(rootNode)

            // Hook for tools/tests: take a screenshot, return false to quit.
            // Runs after draw but before present so the back buffer still
            // holds the final pixels.
            if (onFrame != null && !onFrame(renderBackend, frameIndex)) {
                running = false
            }
            renderBackend.endFrame()
            frameIndex++

            SDL_Delay(16u)
        }

        snapshotHandle.dispose()
        composition.dispose()
        recomposer.cancel()
        recomposeJob.cancelAndJoin()
    }

    // Restore the process-global Dispatchers.Main and drop any pending
    // tasks (the channel is unlimited so this is safe).
    mainDispatcher.close()
    @OptIn(ExperimentalCoroutinesApi::class)
    Dispatchers.resetMain()

    renderBackend.destroy()
    backend.destroy()
}
