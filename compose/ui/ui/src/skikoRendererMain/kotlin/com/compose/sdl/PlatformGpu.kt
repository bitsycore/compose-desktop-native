package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaBridge
import kotlin.experimental.ExperimentalNativeApi

// ==================
// MARK: Metal bridge factory (skia-only)
// ==================

internal expect fun makeMetalBridge(backend: SDL3Backend): SkiaBridge?

actual fun rendererPreferredGpuMode(): GpuMode {
    @OptIn(ExperimentalNativeApi::class)
    return when (Platform.osFamily) {
        OsFamily.MACOSX -> GpuMode.Skia.Metal
        OsFamily.LINUX -> GpuMode.Skia.OpenGL
        // Windows (mingwX64, Route 1a): GPU via Skia's GL backend on an SDL WGL
        // context (SkiaGLBridge, shared with Linux). CPU raster stays available
        // as --gpu=software. (A native D3D12 bridge is the milestone-2 upgrade.)
        OsFamily.WINDOWS -> GpuMode.Skia.OpenGL
        else -> GpuMode.Software
    }
}
