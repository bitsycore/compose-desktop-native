package com.compose.sdl

import kotlin.experimental.ExperimentalNativeApi

// ==================
// MARK: SDL3 renderer per-OS default
// ==================

actual fun rendererPreferredGpuMode(): GpuMode {
    @OptIn(ExperimentalNativeApi::class)
    return when (Platform.osFamily) {
        OsFamily.MACOSX -> GpuMode.Sdl3.Metal
        OsFamily.LINUX -> GpuMode.Sdl3.Auto
        OsFamily.WINDOWS -> GpuMode.Sdl3.Auto
        else -> GpuMode.Sdl3.Auto
    }
}
