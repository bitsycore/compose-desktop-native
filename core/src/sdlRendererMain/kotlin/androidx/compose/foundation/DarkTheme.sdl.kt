package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

// ==================
// MARK: isSystemInDarkTheme native actual
// ==================

/**
 * No platform dark-mode signal hooked up — always returns false. Apps
 * wanting dark / light toggle should drive it from their own state.
 */
@Composable
@ReadOnlyComposable
internal actual fun _isSystemInDarkTheme(): Boolean = false
