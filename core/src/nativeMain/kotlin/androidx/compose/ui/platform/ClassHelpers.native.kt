package androidx.compose.ui.platform

import kotlin.reflect.KClass

// ==================
// MARK: ClassHelpers — native actual
// ==================

/** Cheap type-identity for the runtime's `Any.nativeClass()` perf hook.
 *  Kotlin/Native's `this::class` returns the KClass which is value-stable. */
internal actual fun Any.nativeClass(): Any = this::class
