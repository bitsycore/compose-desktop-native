package com.compose.sdl

import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import sdl3.SDL_GetCurrentThreadID

// ==================
// MARK: Sdl3MainDispatcher
// ==================

/** Single-threaded Main dispatcher driven by the SDL3 main loop. Kotlin/Native
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

   `immediate` is a REAL immediate dispatcher: its isDispatchNeeded() returns
   false when already on the SDL main thread, so work runs inline — matching
   Android's Main.immediate / Swing's EDT semantics. This is load-bearing:
   androidx.lifecycle's KMP LifecycleRegistry enforces main-thread access by
   round-tripping through Dispatchers.Main.immediate (MainDispatcherChecker);
   with a queue-only Main that trip can never complete from the main thread
   while the loop is inside setContent, deadlocking the app at 0% CPU (this
   froze Navigation3's NavDisplay — rememberLifecycleOwner — on mingwX64,
   see NAV_FIX.md). The BASE dispatcher intentionally KEEPS always-queue
   semantics so LaunchedEffect / recomposer ordering is unchanged: state
   writes land at the loop's drainPending(), observable on the same frame. */
internal class Sdl3MainDispatcher : MainCoroutineDispatcher() {

	private val fQueue = Channel<Runnable>(Channel.UNLIMITED)

	// The SDL main thread — the dispatcher is constructed by nativeComposeApp
	// on the thread that then runs the loop.
	private val fMainThreadId = SDL_GetCurrentThreadID()

	override val immediate: MainCoroutineDispatcher = ImmediateDispatcher()

	override fun dispatch(context: CoroutineContext, block: Runnable) {
		fQueue.trySend(block)
	}

	/** Drains all pending tasks. Called from the SDL3 main loop once per
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

	/** Closes the queue. After this, dispatch() drops new posts silently.
	   Called from nativeComposeWindow as part of shutdown. */
	fun close() {
		fQueue.close()
	}

	override fun toString(): String = "Sdl3MainDispatcher"

	/** Dispatchers.Main.immediate: inline when already on the SDL main thread
	   (isDispatchNeeded = false), queued like the base dispatcher otherwise. */
	private inner class ImmediateDispatcher : MainCoroutineDispatcher() {
		override val immediate: MainCoroutineDispatcher get() = this

		override fun isDispatchNeeded(context: CoroutineContext): Boolean =
			SDL_GetCurrentThreadID() != fMainThreadId

		override fun dispatch(context: CoroutineContext, block: Runnable) {
			fQueue.trySend(block)
		}

		override fun toString(): String = "Sdl3MainDispatcher.immediate"
	}
}
