package com.compose.desktop.native

import com.compose.desktop.native.renderer.skia.SkiaBridge

// ==================
// MARK: Linux GPU defaults
// ==================

/* Linux gets OpenGL — Skia GL via SDL3's GL context. Metal isn't a thing
   here. */
actual fun rendererPreferredGpuMode(): GpuMode = GpuMode.Skia.OpenGL

internal actual fun makeMetalBridge(backend: SDL3Backend): SkiaBridge? = null
