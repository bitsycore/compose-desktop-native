package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaBridge
import com.compose.sdl.renderer.skia.SkiaMetalBridge

internal actual fun makeMetalBridge(backend: SDL3Backend): SkiaBridge? {
    val bridge = SkiaMetalBridge(backend)
    return if (bridge.init()) bridge else null
}