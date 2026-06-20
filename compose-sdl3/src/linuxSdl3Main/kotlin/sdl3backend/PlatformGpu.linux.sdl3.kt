package sdl3backend

// ==================
// MARK: Linux (SDL3-only build)
// ==================

/* Compiled when the gradle property `-Prenderer=sdl3` is set. Skiko is
   dropped from the build and the SDL3 renderer is used instead — so
   AUTO resolves to SDL3. */
internal actual fun preferredGpuMode(): GpuMode = GpuMode.SDL3
