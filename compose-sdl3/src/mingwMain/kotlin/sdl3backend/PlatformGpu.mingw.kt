package sdl3backend

// ==================
// MARK: Windows / mingwX64 GPU defaults
// ==================

/* mingwX64 has no Skiko klib, so GPU acceleration via Skia isn't an
   option here. AUTO resolves to SDL3 since that's the only renderer
   actually built on this target. */
internal actual fun preferredGpuMode(): GpuMode = GpuMode.SDL3
