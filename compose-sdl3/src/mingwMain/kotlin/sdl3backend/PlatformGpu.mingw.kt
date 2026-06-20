package sdl3backend

// ==================
// MARK: Windows / mingwX64 GPU defaults
// ==================

/* mingwX64 has no Skiko klib, so Skia.* isn't an option. Sdl3.Auto
   lets SDL pick the best Windows driver (Direct3D 11 on modern
   Windows, fallback to OpenGL or software). */
internal actual fun preferredGpuMode(): GpuMode = GpuMode.Sdl3.Auto
