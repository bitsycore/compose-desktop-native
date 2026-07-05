package com.compose.desktop.native

import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext

// ==================
// MARK: Sdl3MainDispatcher
// ==================

/* Single-threaded Main dispatcher driven by the SDL3 main loop. Kotlin/Native
   ships no Dispatchers.Main on Linux/Windows targets (and the Darwin one
   posts to GCD's main queue, which only drains when the run loop pumps —
   our SDL_Delay-based loop doesn't reliably pump it). nativeComposeWindow
   installs this dispatcher via kotlinx.coroutines.test.setMain at startup
   and resets it on shutdown, so app code can withContext(Dispatchers.Main)
   { ... } portably without any platform-specific setup.

   Internals: every dispatch() enqueues onto an unlimited channel; the main
   loop calls drain() at a fixed point each frame to run all pending
   blocks. Channel.trySend is lock-free, so posts from other threads
   (Dispatchers.IO, Default, etc.) are safe.

   immediate returns `this` — even calls from the main thread go through the
   queue. Simpler than tracking thread identity for marginal benefit, and
   matches the desktop-Compose Main dispatcher semantics where state writes
   should be observable on the same frame they were posted from.
*/
internal class Sdl3MainDispatcher : MainCoroutineDispatcher() {

	private val fQueue = Channel<Runnable>(Channel.UNLIMITED)

	override val immediate: MainCoroutineDispatcher = this

	override fun dispatch(context: CoroutineContext, block: Runnable) {
		fQueue.trySend(block)
	}

	/* Drains all pending tasks. Called from the SDL3 main loop once per
	   frame (after event dispatch, before layout) so withContext(Main) {}
	   resumptions land before the recomposer kicks off the next frame's
	   composition. */
	fun drainPending() {
		while (true) {
			val vTask = fQueue.tryReceive().getOrNull() ?: break
			runCatching { vTask.run() }.onFailure { vErr ->
				println("Sdl3MainDispatcher: dispatched task threw: ${vErr.message}")
			}
		}
	}

	/* Closes the queue. After this, dispatch() drops new posts silently.
	   Called from nativeComposeWindow as part of shutdown. */
	fun close() {
		fQueue.close()
	}

	override fun toString(): String = "Sdl3MainDispatcher"
}
