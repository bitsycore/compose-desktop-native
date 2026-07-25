package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.compose.sdl.res.systemThemeIsDarkProvider

// System dark-theme flag via the platform-env seam (installed by the SDL layer),
// so :foundation carries no dependency on the sdl3 cinterop.
@Composable
@ReadOnlyComposable
internal actual fun _isSystemInDarkTheme(): Boolean = systemThemeIsDarkProvider?.invoke() ?: false
