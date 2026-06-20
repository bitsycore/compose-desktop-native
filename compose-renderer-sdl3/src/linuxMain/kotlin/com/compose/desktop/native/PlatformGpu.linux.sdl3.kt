package com.compose.desktop.native

// ==================
// MARK: Linux (SDL3-only build)
// ==================

/* Compiled when -Prenderer=sdl3 is set. Sdl3.Auto lets SDL3 pick the
   best Linux driver (typically OpenGL; Vulkan if available). */
actual fun rendererPreferredGpuMode(): GpuMode = GpuMode.Sdl3.Auto
