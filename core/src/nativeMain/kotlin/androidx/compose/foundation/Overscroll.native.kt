package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalAccessorScope

// ==================
// MARK: Overscroll actuals — desktop no-op
// ==================

/*
 Actuals for vendored Overscroll.kt's two expect declarations. Desktop/native
 has no built-in overscroll animation (Android/iOS use platform-native bounce);
 both actuals return null, matching upstream's desktopMain actual.
*/
@Composable
internal actual fun rememberPlatformOverscrollEffect(): OverscrollEffect? = null

internal actual fun CompositionLocalAccessorScope.defaultOverscrollFactory(): OverscrollFactory? = null
