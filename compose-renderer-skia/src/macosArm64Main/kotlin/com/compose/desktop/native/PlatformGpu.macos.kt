package com.compose.desktop.native

import com.compose.desktop.native.renderer.skia.SkiaBridge
import com.compose.desktop.native.renderer.skia.SkiaMetalBridge

// ==================
// MARK: macOS GPU defaults
// ==================

actual fun rendererPreferredGpuMode(): GpuMode = GpuMode.Skia.Metal

internal actual fun makeMetalBridge(backend: SDL3Backend): SkiaBridge? {
	val bridge = SkiaMetalBridge(backend)
	return if (bridge.init()) bridge else null
}
