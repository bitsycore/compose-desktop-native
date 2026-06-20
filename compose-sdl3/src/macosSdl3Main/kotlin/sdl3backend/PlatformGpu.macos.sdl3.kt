package sdl3backend

// ==================
// MARK: macOS (SDL3-only build)
// ==================

/* Compiled when the gradle property `-Prenderer=sdl3` is set. Skiko is
   dropped from the build and the SDL3 renderer is used instead — so
   AUTO resolves to SDL3 (the only option available). */
internal actual fun preferredGpuMode(): GpuMode = GpuMode.SDL3
