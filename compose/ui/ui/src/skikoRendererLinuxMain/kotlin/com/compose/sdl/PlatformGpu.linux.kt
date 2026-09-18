package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaBridge

// ==================
// MARK: Linux GPU defaults
// ==================

internal actual fun makeMetalBridge(backend: Sdl3Backend): SkiaBridge? = null
