@file:Suppress("DEPRECATION")

package androidx.compose.ui.platform

// ==================
// MARK: CompositionLocals — native actuals
// ==================

/*
 * Project native actuals for the vendored commonMain CompositionLocals.kt.
 * Upstream ships these in `CompositionLocals.skiko.kt` next to
 * `ProvidePlatformCompositionLocals` (which needs PlatformContext + the
 * full scene engine — blocked here). We provide only the `LocalLifecycleOwner`
 * actual — the rest of CompositionLocals.skiko.kt's extras
 * (LocalPlatformScreenReader / LocalPlatformWindowInsets /
 * LocalPlatformPrefetchScheduler) stay unvendored until the scene engine lands.
 */
actual val LocalLifecycleOwner get() = androidx.lifecycle.compose.LocalLifecycleOwner
