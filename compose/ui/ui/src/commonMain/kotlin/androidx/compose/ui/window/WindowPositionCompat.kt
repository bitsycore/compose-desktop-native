package androidx.compose.ui.window

import kotlin.reflect.KClass

// Upstream WindowPosition.desktop.kt uses JVM javaClass in equals().
// This provides KClass equality on Kotlin/Native so the upstream file vendors byte-for-byte verbatim.
internal inline val Any.javaClass: KClass<*>
    get() = this::class
