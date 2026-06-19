package sdl3backend

import androidx.compose.runtime.*
import compose.ui.Constraints
import compose.ui.input.PointerEventType
import compose.ui.node.LayoutNode
import compose.ui.node.NodeApplier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import sdl3.SDL_Delay
import sdl3.SDL_GetTicksNS

// ==================
// MARK: ComposeWindow
// ==================

/**
 * A MonotonicFrameClock driven by SDL3's main loop.
 * Each frame, we signal awaiting callbacks so the Recomposer
 * can do its work synchronously within the loop iteration.
 */
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

    val rootNode = LayoutNode()
    val frameClock = SDL3FrameClock()

    runBlocking(frameClock) {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(NodeApplier(rootNode), recomposer)

        // Launch recomposer in background
        val recomposeJob = launch { recomposer.runRecomposeAndApplyChanges() }

        composition.setContent(content)

        // ============
        //  Main loop
        var running = true

        while (running) {
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

            // Cap at ~60fps
            SDL_Delay(16u)
        }

        composition.dispose()
        recomposer.cancel()
        recomposeJob.cancelAndJoin()
    }

    renderer.destroy()
    backend.destroy()
}
