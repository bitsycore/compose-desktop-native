package com.compose.sdl.graphics

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

// ==================
// MARK: NativeReleaseQueue — main-thread-deferred native resource disposal
// ==================

/**
 * A queue of native-resource release actions drained on the MAIN THREAD once
 * per frame by the window loop (see ComposeWindow). It exists because the two
 * ways a renderer resource dies are both off the main thread, while the calls
 * that free them (SDL_DestroyTexture, and any renderer API) are main-thread-only:
 *
 *  - a Kotlin/Native `Cleaner` runs on a GC worker thread, and
 *  - explicit `close()` can be reached from composition/effect code on other
 *    dispatchers.
 *
 * Both simply ENQUEUE here; the main loop runs the actions between frames. This
 * turns the GC (issue #2: Cleaner-managed native memory starving because the
 * quiet Kotlin heap rarely triggers a collection) from the disposal MECHANISM
 * into a mere safeguard — ownership closes resources promptly, the Cleaner is
 * the backstop for whatever leaks a `close()`.
 *
 * Actions must be idempotent-safe at the source (guard against a resource being
 * both explicitly closed and later GC-cleaned — see SdlImageBitmap's holder).
 */
object NativeReleaseQueue {

	private val fLock = SynchronizedObject()
	private var fPending = ArrayList<() -> Unit>()

	/** Enqueue a release action. Safe to call from any thread. */
	fun enqueue(action: () -> Unit) {
		synchronized(fLock) { fPending.add(action) }
	}

	/**
	 * Run and clear every queued action. MAIN THREAD ONLY — the actions call
	 * renderer APIs that aren't thread-safe. Returns the number drained.
	 */
	fun drain(): Int {
		val vBatch = synchronized(fLock) {
			if (fPending.isEmpty()) return 0
			val vTaken = fPending
			fPending = ArrayList()
			vTaken
		}
		for (vAction in vBatch) {
			runCatching { vAction() }
		}
		return vBatch.size
	}
}
