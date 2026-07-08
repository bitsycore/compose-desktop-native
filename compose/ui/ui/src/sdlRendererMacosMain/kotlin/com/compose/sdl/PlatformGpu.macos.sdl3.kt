package com.compose.sdl

// ==================
// MARK: macOS (SDL3-only build)
// ==================

/* Compiled when the gradle property `-Prenderer=sdl3` is set. Skiko is
   dropped from the build and SDL3 is the only renderer linked. Sdl3.Metal
   is the natural default on macOS — SDL3's Metal driver maps onto the
   same CAMetalLayer that Skia would have used. */
actual fun rendererPreferredGpuMode(): GpuMode = GpuMode.Sdl3.Metal
