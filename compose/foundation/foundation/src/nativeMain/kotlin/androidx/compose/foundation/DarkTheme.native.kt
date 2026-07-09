package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import sdl3.SDL_GetSystemTheme
import sdl3.SDL_SystemTheme

@Composable
@ReadOnlyComposable
internal actual fun _isSystemInDarkTheme(): Boolean = SDL_GetSystemTheme() == SDL_SystemTheme.SDL_SYSTEM_THEME_DARK