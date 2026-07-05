@file:Suppress("DEPRECATION")

package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi

// ==================
// MARK: CompositionLocals — native actuals + project stubs
// ==================

/*
 * Project native actuals for the vendored commonMain CompositionLocals.kt.
 * Upstream ships these in `CompositionLocals.skiko.kt` next to
 * `ProvidePlatformCompositionLocals` (which needs PlatformContext + the
 * full scene engine — blocked here). We provide the `LocalLifecycleOwner`
 * actual plus the three internal-API composition locals that
 * `foundation-layout/WindowInsets.skiko.kt`, `material3`, and other vendored
 * files reference. Providers land in `ComposeWindow` when we wire real
 * platform integration; today reading them without a provider throws.
 */
actual val LocalLifecycleOwner get() = androidx.lifecycle.compose.LocalLifecycleOwner

// LocalPlatformPrefetchScheduler is provided by PrefetchLocals.native.kt (a
// project stub predating this file — no-op scheduler default, since prefetch is
// a background performance optimization the SDL single-threaded main loop
// doesn't drive).

@InternalComposeUiApi
val LocalPlatformScreenReader = staticCompositionLocalOf<PlatformScreenReader> {
	error("CompositionLocal LocalPlatformScreenReader not present")
}

@InternalComposeUiApi
val LocalPlatformWindowInsets = staticCompositionLocalOf<PlatformWindowInsets> {
	error("CompositionLocal LocalPlatformWindowInsets not present")
}
