package sdl3backend

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeApplier
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
    content: @Composable () -> Unit
) {
    val backend = SDL3Backend(title, width, height)
    if (!backend.init()) {
        println("Failed to init SDL3 backend")
        return
    }

    val renderer = SDL3Renderer(backend)
    if (!renderer.init()) {
        println("Failed to init SDL3 text renderer")
        backend.destroy()
        return
    }

    // Hook the real TTF metrics into the common layout pass so Text bounds match
    // what's actually rendered (fixes off-center text inside Buttons / Boxes).
    currentTextMeasurer = renderer.textMeasurer

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
                        if (event.event.type == PointerEventType.Release) {
                            val hit = rootNode.hitTest(event.event.x, event.event.y)
                            hit?.findClickHandler()?.invoke()
                        }
                    }

                    is AppEvent.Key -> { /* key handling placeholder */ }
                }
            }

            // ============
            //  Signal frame to recomposer
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame()
            yield()

            // ============
            //  Layout
            backend.updateWindowSize()
            val constraints = Constraints.fixed(backend.windowWidth, backend.windowHeight)
            rootNode.measure(constraints)
            rootNode.place(0, 0)

            // ============
            //  Draw
            backend.beginFrame()
            renderer.draw(rootNode)
            backend.endFrame()

            SDL_Delay(16u)
        }

        snapshotHandle.dispose()
        composition.dispose()
        recomposer.cancel()
        recomposeJob.cancelAndJoin()
    }

    renderer.destroy()
    backend.destroy()
}
