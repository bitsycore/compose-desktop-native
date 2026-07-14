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
        else -> GpuMode.Skia.OpenGL
    }
}
