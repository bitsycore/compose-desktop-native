package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaBridge

// ==================
// MARK: Windows (mingwX64) GPU defaults
// ==================

// No Metal on Windows. The default Skia bridge is CPU raster (GpuMode.CpuRaster →
// SkiaSurfaceBridge), so no GPU-context bridge is provided here yet. A D3D/Vulkan
// bridge can be added later (Route 1a milestone 2).
internal actual fun makeMetalBridge(backend: Sdl3Backend): SkiaBridge? = null
