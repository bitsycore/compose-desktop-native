/*
 * Manually vendored from
 *     compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/Synchronization.kt
 * with @PublishedApi added to `SynchronizedObject`. Upstream's
 * `internal expect inline fun synchronized(...)` exposes the internal
 * SynchronizedObject in its inline body, which K2 metadata compile flags as
 * `LESS_VISIBLE_TYPE_ACCESS_IN_INLINE` — the suppression at file-level does
 * NOT clear the error in metadata compile, so promote the class to
 * @PublishedApi so its effective ABI visibility matches the inline fn.
 * Line-for-line kept in sync; the entry in compose/ui/compose-fork.txt is
 * commented out so this file survives `scripts/compose-fork/sync.sh`.
 */
// VENDOR-BASE: compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/Synchronization.kt @ v1.12.0-beta03+dev4483

package androidx.compose.ui.platform

@PublishedApi
internal expect class SynchronizedObject

internal expect inline fun makeSynchronizedObject(ref: Any? = null): SynchronizedObject

internal expect inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R
