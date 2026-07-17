package com.compose.sdl

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import sdl3.SDL_GetTicksNS

/* MonotonicFrameClock for the SDL main loop, with upstream frame semantics:

   - withFrameNanos ALWAYS suspends until the NEXT sendFrame — never completes
     with a stale frame. Deferred animations (AnimatedVisibility's enter/exit
     size + fade, SharedTransition bounds) are registered during the
     measure/draw pass AFTER the recomposition that starts a Transition; the
     transition's UNDISPATCHED frame loop must not observe a frame before
     that pass, or it sees an empty animation list (totalDuration = 0) and
     finishes instantly.
   - one sendFrame wakes ALL waiters (recomposer + every animation coroutine)
     with the SAME timestamp. The previous conflated-Channel implementation
     woke exactly ONE waiter per frame (round-robin), starving concurrent
     animations and de-synchronising their timelines.

   Both come from delegating to the runtime's BroadcastFrameClock, which is
   exactly upstream's dispatch structure. */
internal class SDL3FrameClock : MonotonicFrameClock {
	private val broadcast = BroadcastFrameClock()

	override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
		broadcast.withFrameNanos(onFrame)

	fun sendFrame(inNanos: Long = SDL_GetTicksNS().toLong()) {
		broadcast.sendFrame(inNanos)
	}

	// Coroutines (recomposer / composition animations) currently suspended in withFrameNanos
	// awaiting the next sendFrame — the "an animation is still running" half of the window's
	// quiescence signal (see WindowInstance.hasInvalidations).
	val hasAwaiters: Boolean get() = broadcast.hasAwaiters
}
