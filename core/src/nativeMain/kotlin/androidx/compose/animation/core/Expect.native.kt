package androidx.compose.animation.core

// Native actual for the internal `expect fun getCurrentThread(): Any` in
// animation-core's commonMain/Expect.kt. Upstream only ships an appleMain
// version (NSThread.currentThread); linux/mingw aren't covered. We don't
// enforce main-thread checks in this project, so any stable singleton is
// sufficient — the value is used by InfiniteTransition / Animatable solely
// to compare against a previously-captured thread reference for assertion
// purposes.
internal actual fun getCurrentThread(): Any = sdl3.SDL_GetCurrentThreadID()