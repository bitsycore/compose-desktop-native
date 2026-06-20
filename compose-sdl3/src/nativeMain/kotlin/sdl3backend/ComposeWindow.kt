package sdl3backend

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.FocusableModifier
import androidx.compose.ui.HoverableModifier
import androidx.compose.ui.KeyEventDispatch
import androidx.compose.ui.OnDragModifier
import androidx.compose.ui.OnPressedModifier
import androidx.compose.ui.PressableModifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeApplier
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.text.currentTextMeasurer
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import sdl3.SDL_Delay
import sdl3.SDL_GetTicksNS

// ==================
// MARK: ComposeWindow
// ==================

private class SDL3FrameClock : MonotonicFrameClock {
    private val frameCh = Channel<Long>(Channel.CONFLATED)

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        frameCh.receive()
        return onFrame(currentTimeNanos())
    }

    fun sendFrame() {
        frameCh.trySend(currentTimeNanos())
    }

    private fun currentTimeNanos(): Long = SDL_GetTicksNS().toLong()
}

fun composeWindow(
    title: String = "ComposeNativeSDL3",
    width: Int = 800,
    height: Int = 600,
    gpu: GpuMode = GpuMode.NONE,
    onFrame: ((bridge: SkiaBridge, frameIndex: Int) -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    // AUTO → resolve to the platform's preferred backend (METAL on macOS,
    // OPENGL on Linux). Explicit modes pass through.
    val gpuMode = if (gpu == GpuMode.AUTO) preferredGpuMode() else gpu
    val backend = SDL3Backend(title, width, height, gpuMode = gpuMode)
    if (!backend.init()) {
        println("Failed to init SDL3 backend")
        return
    }
    // Pull the real (HiDPI-aware) pixel dimensions before we hand them to
    // a bridge — without this they default to the logical window size and
    // Retina back buffers come out half-resolution.
    backend.updateWindowSize()

    val skiaBridge: SkiaBridge = when (gpuMode) {
        GpuMode.METAL -> {
            val metal = makeMetalBridge(backend)
            if (metal == null || !metal.ensureSize(backend.pixelWidth, backend.pixelHeight)) {
                println("Failed to init Skia Metal bridge")
                backend.destroy()
                return
            }
            metal
        }
        GpuMode.OPENGL -> {
            val gl = SkiaGLBridge(backend)
            if (!gl.init() || !gl.ensureSize(backend.pixelWidth, backend.pixelHeight)) {
                println("Failed to init Skia GL bridge")
                backend.destroy()
                return
            }
            gl
        }
        GpuMode.NONE -> {
            val raster = SkiaSurfaceBridge(backend)
            if (!raster.ensureSize(backend.pixelWidth, backend.pixelHeight)) {
                println("Failed to init Skia raster bridge")
                backend.destroy()
                return
            }
            raster
        }
        GpuMode.AUTO -> error("unreachable — resolved above")
    }

    val textRenderer = SkiaTextRenderer()
    val renderer = SkiaRenderer(textRenderer)

    // Hook Skia font metrics into the common layout pass so Text bounds match
    // what's actually drawn (fixes off-centre text in Buttons / Boxes).
    currentTextMeasurer = textRenderer.textMeasurer

    // Wire SDL3 clipboard so TextField Cmd+C / Cmd+V work in commonMain.
    currentClipboard = SDL3Clipboard()

    val rootNode = LayoutNode()
    val frameClock = SDL3FrameClock()

    runBlocking(frameClock) {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(NodeApplier(rootNode), recomposer)

        val recomposeJob = launch { recomposer.runRecomposeAndApplyChanges() }

        // Without this, mutableStateOf writes from click handlers never reach the
        // recomposer and the UI silently stops updating after the first frame.
        val snapshotHandle = Snapshot.registerGlobalWriteObserver {
            Snapshot.sendApplyNotifications()
        }

        composition.setContent(content)

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

        fun cursorInsideNode(inHit: LayoutNode?, inNode: LayoutNode): Boolean =
            generateSequence(inHit) { it.parent }.any { it === inNode }

        while (running) {
            // ============
            //  Drain any pending snapshot writes from the previous frame's handlers
            Snapshot.sendApplyNotifications()

            // ============
            //  Events
            val events = pollEvents()
            for (event in events) {
                when (event) {
                    is AppEvent.Quit -> running = false

                    is AppEvent.WindowResized -> {
                        backend.updateWindowSize()
                    }

                    is AppEvent.Pointer -> {
                        val vPx = event.event.x
                        val vPy = event.event.y
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
                                // Walk from the hit-test target up, firing each OnPressedModifier
                                // with coordinates relative to that node's absolute top-left.
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
                        focusedNode?.dispatchKeyEvent(KeyEventDispatch(event.event))
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
                        if (vScrollY != null && event.deltaY != 0f) {
                            vScrollY.scrollBy(-(event.deltaY * 50f).toInt())
                        }
                        val vScrollX = vHit?.findHorizontalScrollAncestor()
                        if (vScrollX != null && event.deltaX != 0f) {
                            vScrollX.scrollBy(-(event.deltaX * 50f).toInt())
                        }
                    }
                }
            }

            // ============
            //  Signal frame to recomposer
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame()
            yield()

            // ============
            //  Layout — also resize the Skia surface if the window changed.
            backend.updateWindowSize()
            skiaBridge.ensureSize(backend.pixelWidth, backend.pixelHeight)
            val constraints = Constraints.fixed(backend.windowWidth, backend.windowHeight)
            rootNode.measure(constraints)
            rootNode.place(0, 0)

            // ============
            //  Draw via Skia. Scale by DPR so the logical-point layout maps
            //  to physical pixels on HiDPI displays — text and shapes stay
            //  crisp on Retina instead of getting upscaled by the OS.
            val canvas = skiaBridge.canvas
            val vDpr = backend.pixelDensity
            canvas.save()
            if (vDpr != 1f) canvas.scale(vDpr, vDpr)
            renderer.draw(rootNode, canvas)
            canvas.restore()

            // Hook for tools/tests: take a screenshot, return false to quit.
            // Runs after draw but before present so the bridge's surface
            // still holds the final pixels.
            if (onFrame != null && !onFrame(skiaBridge, frameIndex)) {
                running = false
            }
            skiaBridge.present()
            frameIndex++

            SDL_Delay(16u)
        }

        snapshotHandle.dispose()
        composition.dispose()
        recomposer.cancel()
        recomposeJob.cancelAndJoin()
    }

    textRenderer.destroy()
    skiaBridge.destroy()
    backend.destroy()
}
