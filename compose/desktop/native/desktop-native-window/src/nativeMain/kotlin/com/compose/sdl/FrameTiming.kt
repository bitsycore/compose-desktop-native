@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
)

package com.compose.sdl

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.pointed
import kotlinx.coroutines.CancellationException
import sdl3.SDL_GetCurrentDisplayMode
import sdl3.SDL_GetDisplayForWindow
import sdl3.SDL_GetTicks
import sdl3.SDL_GetTicksNS

// ==================
// MARK: Deterministic frame timing + probe/screenshot support
// ==================

/** Set BEFORE nativeComposeApp/nativeComposeWindow: every window's composition then runs
   under an InfiniteAnimationPolicy that CANCELS infinite animations, so
   rememberInfiniteTransition & co. freeze at their initial value - the same mechanism
   upstream's test rules use. Screenshot/parity runs enable it so looping screens can
   reach quiescence and capture deterministically. */
var disableInfiniteAnimations: Boolean = false

/** The cancelling policy: a coroutine that ends in CancellationException counts as
   cancelled (not failed), so only the animation coroutine stops - nothing propagates. */
internal object CancelInfiniteAnimationsPolicy : androidx.compose.ui.platform.InfiniteAnimationPolicy {
	override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
		throw CancellationException("infinite animations are disabled (disableInfiniteAnimations)")
}

/** Set BEFORE nativeComposeApp/nativeComposeWindow: the composition + animation frame
   clocks advance a VIRTUAL 16.6ms per main-loop iteration instead of reading SDL's
   real-time ticks - the native mirror of the JVM parity leg's render(nanos) stepping.
   Animations then progress by exact per-frame deltas, so anything time-raced (e.g. a
   bring-into-view scroll interrupted mid-flight) resolves identically on every run and
   screenshots become deterministic. Input timestamps and FPS stay on real time. */
var useVirtualFrameTime: Boolean = false

private var virtualFrameNanos = 0L

internal fun advanceVirtualFrame() {
	if (useVirtualFrameTime) virtualFrameNanos += 16_666_667L
}

// Timestamp for the composition frame clocks (recomposer + withFrameNanos animations).
internal fun frameClockNanos(): Long =
	if (useVirtualFrameTime) virtualFrameNanos else SDL_GetTicksNS().toLong()

// Timestamp for the owner's node-animation clock - real path keeps the pre-existing
// ms-resolution SDL_GetTicks base so non-screenshot behaviour is bit-for-bit unchanged.
internal fun animationClockNanos(): Long =
	if (useVirtualFrameTime) virtualFrameNanos else SDL_GetTicks().toLong() * 1_000_000L

// The window currently inside renderFrame - the loop is single-threaded, so a plain
// var is enough for onFrame probes to address "the window I'm being called for".
internal var renderingWindow: WindowInstance? = null

/** From inside an onFrame callback: true while the just-rendered window still has pending
   work (recomposition, layout/draw invalidation, or an animation awaiting the next frame).
   Screenshot probes capture once this stays false for a few consecutive frames instead of
   at a fixed frame count. Outside onFrame it answers false. */
fun windowHasInvalidations(): Boolean = renderingWindow?.hasInvalidations() ?: false

/** Trigger a Kotlin/Native GC so Cleaner-managed renderer resources release
   their native memory (see the main loop's native-memory nudge). */
@OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
internal fun collectNativeGarbage() = kotlin.native.runtime.GC.collect()

/** Frame interval (ms) of the window's current display, for the non-vsync
   fallback pacing (SDL_Delay). Falls back to 16ms (~60Hz) when the mode can't
   be read, so a 144Hz panel on a non-vsync path isn't capped to 60. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal fun displayFrameDelayMs(window: COpaquePointer?): UInt {
	val vWindow = window ?: return 16u
	val vDisplay = SDL_GetDisplayForWindow(vWindow.reinterpret())
	val vMode = SDL_GetCurrentDisplayMode(vDisplay) ?: return 16u
	val vHz = vMode.pointed.refresh_rate
	return if (vHz > 0f) (1000f / vHz).toUInt().coerceAtLeast(1u) else 16u
}
