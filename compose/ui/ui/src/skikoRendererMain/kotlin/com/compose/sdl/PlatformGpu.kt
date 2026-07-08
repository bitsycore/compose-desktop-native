package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaBridge

// ==================
// MARK: Metal bridge factory (skia-only)
// ==================

/* Constructs the platform-specific Metal bridge, or null if Metal isn't
   supported on this target. Linux returns null; macOS returns the real
   thing. Internal — only used by this module's createRenderBackend. */
internal expect fun makeMetalBridge(backend: SDL3Backend): SkiaBridge?

/* What GpuMode.Auto resolves to when Skia is the active renderer: macOS →
   Skia.Metal, Linux → Skia.OpenGL. Public — :window's preferredGpuMode
   actual delegates here. */
expect fun rendererPreferredGpuMode(): GpuMode
