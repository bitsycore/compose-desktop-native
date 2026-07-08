package com.compose.sdl.renderer.skia

import com.compose.sdl.*

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image

// ==================
// MARK: SkiaBridge
// ==================

/* Backend-agnostic Skia rendering surface. ComposeWindow drives it once
   per frame; the concrete implementation owns whatever GPU / CPU
   resources it needs (raster buffer + SDL_Texture, or a GL/Metal backend
   render target). */
interface SkiaBridge {
    val canvas: Canvas
    fun ensureSize(inWidth: Int, inHeight: Int): Boolean
    fun present()
    fun destroy()
    // Snapshot of the current frame as a Skia Image (may be GPU-backed —
    // call makeRasterFrame for a CPU-side copy).
    fun snapshot(): Image?
    // Returns (width, height, bgra-bytes) for the current frame, or null if
    // unavailable. Bytes are 32-bit BGRA, premultiplied alpha, top-down. */
    fun snapshotBgra(): Triple<Int, Int, ByteArray>?
}
