package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaBridge

// ==================
// MARK: Linux GPU defaults
// ==================

internal actual fun makeMetalBridge(backend: SDL3Backend): SkiaBridge? = null
