package com.compose.desktop.native.renderer.skia

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.ImageLoader
import androidx.compose.ui.res.ResourceKind
import com.compose.desktop.native.text.TextMeasurer
import com.compose.desktop.native.*
import org.jetbrains.skia.Canvas

// ==================
// MARK: SkiaRenderBackend
// ==================

/* RenderBackend that paints through Skia. Picks the concrete SkiaBridge
   (CPU raster, OpenGL, or Metal) from gpuMode. Canvas is scaled by the
   per-frame DPR so the layout (in logical points) maps to physical pixels.

   AUTO is resolved at the call site (ComposeWindow); this class only
   sees concrete modes.

   NOTE: Phase 9 pivoted rendering to the upstream engine via drawRoot(host).
   A real SkiaCanvas : androidx.compose.ui.graphics.Canvas actual is TODO —
   until then the Skia path compiles but paints nothing (drawRoot inherits
   the RenderBackend default no-op). */
internal class SkiaRenderBackend(
    private val sdl: SDL3Backend,
    private val gpuMode: GpuMode,
) : RenderBackend {

    private val fBridge: SkiaBridge = buildBridge()
    private val fSkiaTextRenderer = SkiaTextRenderer()
    private val fSkiaImageCache = SkiaImageCache()
    private var fCurrentCanvas: Canvas? = null

    override val textMeasurer: TextMeasurer
        get() = fSkiaTextRenderer.textMeasurer

    override val imageLoader: ImageLoader = object : ImageLoader {
        override fun intrinsicSize(inPath: String, inKind: ResourceKind): Size =
            fSkiaImageCache.intrinsicSize(inPath, inKind)
        override fun readBytes(inPath: String): ByteArray? =
            loadComposeResourceBytes(inPath)
    }

    private fun buildBridge(): SkiaBridge = when (gpuMode) {
        is GpuMode.Skia.Metal  -> makeMetalBridge(sdl) ?: error("Skia.Metal isn't supported on this target")
        is GpuMode.Skia.OpenGL -> SkiaGLBridge(sdl).also { require(it.init()) { "Skia.OpenGL init failed" } }
        is GpuMode.Software        -> SkiaSurfaceBridge(sdl)
        is GpuMode.Sdl3,
        is GpuMode.Auto        -> error("SkiaRenderBackend received non-Skia gpuMode $gpuMode")
    }

    override fun ensureSize(inPixelWidth: Int, inPixelHeight: Int): Boolean =
        fBridge.ensureSize(inPixelWidth, inPixelHeight)

    override fun beginFrame(inDpr: Float) {
        val canvas = fBridge.canvas
        canvas.save()
        if (inDpr != 1f) canvas.scale(inDpr, inDpr)
        fCurrentCanvas = canvas
    }

    override fun endFrame() {
        fCurrentCanvas?.restore()
        fCurrentCanvas = null
        fBridge.present()
    }

    override fun snapshotBgra(): Triple<Int, Int, ByteArray>? = fBridge.snapshotBgra()

    override fun destroy() {
        fSkiaImageCache.destroy()
        fSkiaTextRenderer.destroy()
        fBridge.destroy()
    }
}
