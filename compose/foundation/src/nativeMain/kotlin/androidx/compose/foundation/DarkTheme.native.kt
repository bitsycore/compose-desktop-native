package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * No platform dark-mode signal is wired up on native. Always returns false —
 * apps that want a dark/light toggle should drive it from their own state
 * (see :apidemo and :demo for examples).
 *
 * The renderer-split actuals (DarkTheme.skiko.kt + DarkTheme.sdl.kt) were
 * dropped when :foundation was extracted from :core: expect/actual must live
 * in the same module, and duplicating the skikoRenderer/sdlRenderer source
 * set hierarchy on :foundation just to read `LocalSystemTheme` on Skia was a
 * lot of build wiring for one function that no in-repo app calls.
 */
@Composable
@ReadOnlyComposable
internal actual fun _isSystemInDarkTheme(): Boolean = false
