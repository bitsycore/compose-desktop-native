/*
 * Manually vendored from
 *     compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/platform/Synchronization.kt
 * with @PublishedApi added to `SynchronizedObject` — same reason as
 * ui/platform/Synchronization.kt in this module. The entry in
 * compose/ui/compose-fork.txt is commented out so this file survives
 * `scripts/compose-fork/sync.sh`.
 */
// VENDOR-BASE: compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/platform/Synchronization.kt @ v1.12.0-beta03+dev4483

package androidx.compose.ui.text.platform

@PublishedApi
internal expect class SynchronizedObject

internal expect inline fun makeSynchronizedObject(ref: Any? = null): SynchronizedObject

@PublishedApi
@Suppress("LESS_VISIBLE_TYPE_ACCESS_IN_INLINE_WARNING") // b/446705238
internal expect inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R
