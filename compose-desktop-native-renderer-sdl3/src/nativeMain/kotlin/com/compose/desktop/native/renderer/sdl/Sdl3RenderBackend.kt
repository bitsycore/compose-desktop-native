package com.compose.desktop.native.renderer.sdl

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.res.ImageLoader
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntSize
import com.compose.desktop.native.*
import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: Sdl3RenderBackend
// ==================

/* RenderBackend that uses only SDL3 primitives + SDL3_ttf — no Skia,
   no Skiko. The only renderer on mingwX64. Constructed by the
   makeRenderBackend actual in the core package.

   Per-frame flow:
     beginFrame  → SDL_SetRenderScale to apply the DPR
     draw        → Sdl3Renderer walks the layout tree
     endFrame    → SDL_RenderPresent
*/
internal class Sdl3RenderBackend(private val backend: SDL3Backend) : RenderBackend {

    private val fTextRenderer = Sdl3TextRenderer(backend)
    private val fImageCache = Sdl3ImageCache(backend)
    private val fRenderer: Sdl3Renderer

    init {
        if (!fTextRenderer.init()) {
            error("Sdl3RenderBackend: SDL3_ttf failed to init")
        }
        fRenderer = Sdl3Renderer(backend, fTextRenderer, fImageCache)
    }

    override val textMeasurer: TextMeasurer
        get() = fTextRenderer.textMeasurer

    override val imageLoader: ImageLoader = object : ImageLoader {
        override fun intrinsicSize(inPath: String, inKind: ResourceKind): IntSize =
            fImageCache.intrinsicSize(inPath, inKind)
        override fun readBytes(inPath: String): ByteArray? =
            loadComposeResourceBytes(inPath)
    }

    override fun ensureSize(inPixelWidth: Int, inPixelHeight: Int): Boolean {
        // SDL_Renderer auto-resizes with the window — nothing to do.
        return inPixelWidth > 0 && inPixelHeight > 0
    }

    override fun beginFrame(inDpr: Float) {
        val r = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        // SDL_SetRenderScale stretches every render coord by (dpr, dpr) so
        // logical-point geometry lands at the right physical pixel. Text
        // textures are rasterised at the DPR-scaled font size and drawn
        // at logical-size dst rects — the stretch then maps them 1:1 to
        // physical pixels instead of upscaling a half-resolution glyph.
        SDL_SetRenderScale(r, inDpr, inDpr)
        fTextRenderer.setDpr(inDpr)
    }

    override fun draw(inRoot: LayoutNode) {
        fRenderer.draw(inRoot)
    }

    override fun endFrame() {
        val r = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        SDL_RenderPresent(r)
    }

    /* Read the renderer output into a host BGRA byte array. The renderer's
       native pixel format is platform-dependent (Metal on macOS uses BGRA,
       OpenGL on Linux uses RGBA, etc.), so we explicitly convert the
       returned surface to BGRA32 to share one byte order with the BMP
       writer / the Skia bridge. */
    override fun snapshotBgra(): Triple<Int, Int, ByteArray>? {
        val r = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return null
        val vRaw = SDL_RenderReadPixels(r, null) ?: return null
        // SDL_PIXELFORMAT_BGRA32 = 376840196 in SDL3 (kept literal to avoid a
        // brittle enum lookup that drifts across cinterop versions).
        val vConverted = SDL_ConvertSurface(vRaw.reinterpret(), SDL_PIXELFORMAT_BGRA32) ?: run {
            SDL_DestroySurface(vRaw.reinterpret())
            return null
        }
        SDL_DestroySurface(vRaw.reinterpret())
        try {
            val s = vConverted.reinterpret<SDL_Surface>().pointed
            val w = s.w
            val h = s.h
            val pitch = s.pitch
            val pixels = s.pixels?.reinterpret<UByteVar>() ?: return null
            val out = ByteArray(w * h * 4)
            for (y in 0 until h) {
                val srcRow = y * pitch
                val dstRow = y * w * 4
                for (x in 0 until w * 4) {
                    out[dstRow + x] = pixels[srcRow + x].toByte()
                }
            }
            return Triple(w, h, out)
        } finally {
            SDL_DestroySurface(vConverted)
        }
    }

    override fun destroy() {
        fRenderer.destroy()
        fImageCache.destroy()
        fTextRenderer.destroy()
    }
}
