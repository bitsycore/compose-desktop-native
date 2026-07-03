package androidx.compose.ui.text.platform

// ==================
// MARK: ui.text.platform synchronization (project)
// ==================

/*
 The vendored upstream Synchronization.kt (ui.text.platform) has a public-inline / internal-type
 visibility pattern that upstream suppresses as a warning but our compiler treats as an error, so we
 provide a project version. The SDL build's text layout cache is accessed only from the single-
 threaded main loop, so the lock is a no-op. (Distinct from androidx.compose.ui.platform's own
 atomicfu-backed SynchronizedObject, which is a different package.)
*/
class SynchronizedObject

fun makeSynchronizedObject(ref: Any? = null): SynchronizedObject = SynchronizedObject()

inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R = block()
