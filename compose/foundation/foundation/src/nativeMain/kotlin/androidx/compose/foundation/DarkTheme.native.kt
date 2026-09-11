// VENDOR-REIMPL: compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/DarkTheme.skiko.kt @ v1.12.0
// Project REIMPLEMENTATION, not a derived copy: same package + signatures,
// different body (the port replaces upstream's skiko scene/platform layer).
// Upstream edits to the counterpart do NOT need reconciling here - re-check
// only if the expect/actual SIGNATURES change.

package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.compose.sdl.res.systemThemeIsDarkProvider

// System dark-theme flag via the platform-env seam (installed by the SDL layer),
// so :foundation carries no dependency on the sdl3 cinterop.
@Composable
@ReadOnlyComposable
internal actual fun _isSystemInDarkTheme(): Boolean = systemThemeIsDarkProvider?.invoke() ?: false
