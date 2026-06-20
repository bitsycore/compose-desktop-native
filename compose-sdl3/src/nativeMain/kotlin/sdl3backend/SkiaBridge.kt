package sdl3backend

import org.jetbrains.skia.Canvas

// ==================
// MARK: SkiaBridge
// ==================

/* Backend-agnostic Skia rendering surface. ComposeWindow drives it once
   per frame; the concrete implementation owns whatever GPU / CPU
   resources it needs (raster buffer + SDL_Texture, or a GL/Metal backend
   render target). */
internal interface SkiaBridge {
    val canvas: Canvas
    fun ensureSize(inWidth: Int, inHeight: Int): Boolean
    fun present()
    fun destroy()
}
