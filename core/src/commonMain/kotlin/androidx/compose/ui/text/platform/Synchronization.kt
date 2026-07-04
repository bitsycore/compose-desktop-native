package androidx.compose.ui.text.platform

import kotlinx.atomicfu.locks.SynchronizedObject as AtomicfuSynchronizedObject
import kotlinx.atomicfu.locks.synchronized as atomicfuSynchronized

// ==================
// MARK: ui.text.platform synchronization (project — irreducible exception)
// ==================

/*
 Project version of upstream `ui-text/commonMain/Synchronization.kt` +
 `Synchronization.skiko.kt`. Upstream declares the pair as
 `internal expect (inline) …` with `@PublishedApi` + `@Suppress
 ("LESS_VISIBLE_TYPE_ACCESS_IN_INLINE_WARNING")`. In Kotlin 2.x that diagnostic
 is `LESS_VISIBLE_TYPE_ACCESS_IN_INLINE_ERROR` (severity=ERROR by construction);
 `-Xwarning-level=…:disabled` refuses to downgrade errors + the source-level
 `@Suppress` doesn't apply either. Hard block until Kotlin loosens the check
 (b/446705238) or upstream restructures.

 Vendored callers (`TextLayoutResult.kt`, `UnresolvedSymbolsRegistry.skiko.kt`,
 …) import `androidx.compose.ui.text.platform.SynchronizedObject / synchronized`
 by fully-qualified path, so the type MUST live in this exact package —
 documented in FIDELITY as an irreducible exception.

 Implementation is atomicfu-backed (user's suggestion) rather than a plain
 no-op: text layout / paragraph caches are hit from the recomposer coroutine +
 the main draw thread, so locking is real. The API surface (`SynchronizedObject`
 class, `makeSynchronizedObject()` factory, `synchronized(lock) { … }` block
 fn) exactly matches upstream.
*/

class SynchronizedObject {
	@PublishedApi
	internal val lock: AtomicfuSynchronizedObject = AtomicfuSynchronizedObject()
}

fun makeSynchronizedObject(ref: Any? = null): SynchronizedObject = SynchronizedObject()

inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R =
	atomicfuSynchronized(lock.lock, block)
