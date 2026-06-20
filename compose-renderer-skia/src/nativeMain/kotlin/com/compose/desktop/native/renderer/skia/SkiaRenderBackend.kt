package com.compose.desktop.native.renderer.skia

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.res.ImageLoader
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntSize
import com.compose.desktop.native.*
import org.jetbrains.skia.Canvas

// ==================
// MARK: SkiaRenderBackend
// ==================

/* RenderBackend that paints through Skia. Picks the concrete SkiaBridge
   (CPU raster, OpenGL, or Metal) from gpuMode and re-uses the existing
   SkiaRenderer / SkiaTextRenderer pair. Canvas is scaled by the per-frame
   DPR so the layout (in logical points) maps to physical pixels.

   AUTO is resolved at the call site (ComposeWindow); this class only
   sees concrete modes. */
internal class SkiaRenderBackend(
    private val sdl: SDL3Backend,
    private val gpuMode: GpuMode,
) : RenderBackend {

    private val fBridge: SkiaBridge = buildBridge()
    private val fSkiaTextRenderer = SkiaTextRenderer()
    private val fSkiaImageCache = SkiaImageCache()
    private val fSkiaRenderer = SkiaRenderer(fSkiaTextRenderer, fSkiaImageCache)
    private var fCurrentCanvas: Canvas? = null

    override val textMeasurer: TextMeasurer
        get() = fSkiaTextRenderer.textMeasurer

    override val imageLoader: ImageLoader = object : ImageLoader {
        override fun intrinsicSize(inPath: String, inKind: ResourceKind): IntSize =
            fSkiaImageCache.intrinsicSize(inPath, inKind)
        override fun readBytes(inPath: String): ByteArray? =
            loadComposeResourceBytes(inPath)
    }

    private fun buildBridge(): SkiaBridge = when (gpuMode) {
        is GpuMode.Skia.Metal  -> makeMetalBridge(sdl) ?: error("Skia.Metal isn't supported on this target")
        is GpuMode.Skia.OpenGL -> SkiaGLBridge(sdl).also { require(it.init()) { "Skia.OpenGL init failed" } }
        is GpuMode.None        -> SkiaSurfaceBridge(sdl)
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

    override fun draw(inRoot: LayoutNode) {
        val canvas = fCurrentCanvas ?: return
        fSkiaRenderer.draw(inRoot, canvas)
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
