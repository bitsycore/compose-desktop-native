package com.compose.sdl.renderer.sdl

import androidx.compose.ui.geometry.Size
import com.compose.sdl.res.ImageLoader
import com.compose.sdl.res.ResourceKind
import com.compose.sdl.text.TextMeasurer
import com.compose.sdl.*
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
     drawRoot    → drive the upstream LayoutNode tree through Sdl3Canvas
     endFrame    → SDL_RenderPresent
*/
internal class Sdl3RenderBackend(private val backend: SDL3Backend) : RenderBackend {

    private val fTextRenderer = Sdl3TextRenderer(backend)
    private val fImageCache = Sdl3ImageCache(backend)
    // Persistent offscreen render targets for rounded-shape clipping. Created
    // lazily on the first drawRoot (the renderer must exist first) and reused
    // across frames — allocated here, not per-frame Sdl3Canvas.
    private var fClipTargets: Sdl3ClipTargets? = null
    private var fShadowCache: Sdl3ShadowCache? = null

    init {
        if (!fTextRenderer.init()) {
            error("Sdl3RenderBackend: SDL3_ttf failed to init")
        }
    }

    override val textMeasurer: TextMeasurer
        get() = fTextRenderer.textMeasurer

    override val imageLoader: ImageLoader = object : ImageLoader {
        override fun intrinsicSize(inPath: String, inKind: ResourceKind): Size =
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
        // Clear the frame to Material's dark background (0x121212). Without this
        // the SDL back buffer holds whatever was previously in GPU memory —
        // uncovered regions of the composition (e.g. apidemo's transparent
        // panels) show garbage / pink on macOS Metal. Was previously done by
        // the legacy Sdl3Renderer.draw entry which we retired.
        //
        // Order matters: clear at physical pixel scale (scale=1) so the clear
        // covers the FULL back buffer, THEN apply DPR scale for subsequent draws.
        // Otherwise on retina we clear only 1/DPR of the target and the rest
        // shows garbage. Also reset any stray clip left by the previous frame.
        SDL_SetRenderScale(r, 1f, 1f)
        SDL_SetRenderClipRect(r, null)
        SDL_SetRenderDrawColor(r, 0x12u, 0x12u, 0x12u, 0xFFu)
        SDL_RenderClear(r)
        // SDL_SetRenderScale stretches every render coord by (dpr, dpr) so
        // logical-point geometry lands at the right physical pixel. Text
        // textures are rasterised at the DPR-scaled font size and drawn
        // at logical-size dst rects — the stretch then maps them 1:1 to
        // physical pixels instead of upscaling a half-resolution glyph.
        SDL_SetRenderScale(r, inDpr, inDpr)
        fTextRenderer.setDpr(inDpr)
    }

    // Paint the upstream LayoutNode tree through the vendored pipeline.
    // inHost.rootNode.draw → NodeCoordinator.draw → DrawModifierNode → CanvasDrawScope
    // → Sdl3Canvas → SDL_RenderGeometry.
    override fun drawRoot(inHost: com.compose.sdl.node.ComposeRootHost) {
        val vRenderer = backend.renderer ?: return
        val vClipTargets = fClipTargets ?: Sdl3ClipTargets(vRenderer).also { fClipTargets = it }
        val vShadowCache = fShadowCache ?: Sdl3ShadowCache(vRenderer).also { fShadowCache = it }
        // Register the offscreen (ImageBitmap) render path so the vendored VectorPainter /
        // DrawCache pipeline — hence material3's ImageVector icons — renders. Idempotent.
        if (com.compose.sdl.graphics.offscreenRenderer == null) {
            com.compose.sdl.graphics.offscreenRenderer = Sdl3OffscreenRenderer(vRenderer, fTextRenderer)
        }
        val vCanvas = Sdl3Canvas(
            vRenderer,
            androidx.compose.ui.geometry.Size(backend.pixelWidth.toFloat(), backend.pixelHeight.toFloat()),
            fTextRenderer,
            fImageCache,
            vClipTargets,
            vShadowCache,
        )
        // Expose the frame canvas so an offscreen render can flush it before borrowing
        // the render target (z-order).
        currentMainCanvas = vCanvas
        inHost.rootNode.draw(vCanvas, null)
        vCanvas.finish()
        currentMainCanvas = null
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
        fShadowCache?.destroy()
        fClipTargets?.destroy()
        fImageCache.destroy()
        fTextRenderer.destroy()
    }
}
