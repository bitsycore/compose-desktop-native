package androidx.compose.animation.core

// Upstream only ships an appleMain version (NSThread.currentThread); linux/mingw aren't covered.
// So we use SDL_GetCurrentThreadID() for all platforms.
internal actual fun getCurrentThread(): Any = sdl3.SDL_GetCurrentThreadID()