package sdl3backend

// ==================
// MARK: Linux (SDL3-only build)
// ==================

/* Compiled when -Prenderer=sdl3 is set. Sdl3.Auto lets SDL3 pick the
   best Linux driver (typically OpenGL; Vulkan if available). */
internal actual fun preferredGpuMode(): GpuMode = GpuMode.Sdl3.Auto
