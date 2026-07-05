package androidx.compose.ui.platform

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// Native actual for vendored commonMain `Synchronization.kt`. Same body
// as upstream's `Synchronization.skiko.kt` but without `@PublishedApi` on
// the synchronized fn — upstream's annotation triggers a visibility
// mismatch under our K2 toolchain (`SynchronizedObject` is plain `internal
// actual`, not `@PublishedApi`). The behaviour is identical to the
// vendored `androidx.compose.foundation.platform.Synchronization.native.kt`.
internal actual class SynchronizedObject : kotlinx.atomicfu.locks.SynchronizedObject()

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun makeSynchronizedObject(ref: Any?): SynchronizedObject = SynchronizedObject()

@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
	contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
	return kotlinx.atomicfu.locks.synchronized(lock, block)
}
