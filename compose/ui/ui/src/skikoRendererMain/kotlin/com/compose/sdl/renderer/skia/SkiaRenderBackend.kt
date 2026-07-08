package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.Size
import com.compose.sdl.res.ImageLoader
import com.compose.sdl.res.ResourceKind
import com.compose.sdl.text.TextMeasurer
import com.compose.sdl.*
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color as SkColor

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
        // Clear the surface to Material dark. Without this the Metal back
        // buffer shows uninitialized GPU memory (pink) since Sdl3Renderer.draw
        // no longer runs and drawRoot is a no-op inherited default until
        // SkiaCanvas lands. Clearing before scale so it covers the whole
        // physical target.
        canvas.clear(SkColor.makeARGB(0xFF, 0x12, 0x12, 0x12))
        canvas.save()
        if (inDpr != 1f) canvas.scale(inDpr, inDpr)
        // Sp-valued span sizes resolve through resolveRunPx, which needs the
        // same LocalDensity DPR the paragraph used to bake base fontPx (16sp *
        // density=2 → 32px). ComposeWindow calls beginFrame(1f) — layout is
        // already in physical pixels — so use backend.pixelDensity directly
        // here or Sp spans render at logical-point sizes and look tiny next
        // to their base text.
        fSkiaTextRenderer.setDensity(sdl.pixelDensity)
        fCurrentCanvas = canvas
    }

    override fun drawRoot(inHost: com.compose.sdl.node.ComposeRootHost) {
        val canvas = fCurrentCanvas ?: return
        // Register the offscreen (ImageBitmap) render path so the vendored
        // VectorPainter / DrawCache pipeline — hence material3's ImageVector
        // icons — renders. Idempotent.
        if (com.compose.sdl.graphics.offscreenRenderer == null) {
            com.compose.sdl.graphics.offscreenRenderer =
                SkiaOffscreenRenderer(fSkiaTextRenderer, fSkiaImageCache)
        }
        val vSize = androidx.compose.ui.geometry.Size(sdl.pixelWidth.toFloat(), sdl.pixelHeight.toFloat())
        val vCanvas = SkiaCanvas(canvas, vSize, fSkiaTextRenderer, fSkiaImageCache)
        inHost.rootNode.draw(vCanvas, null)
        vCanvas.finish()
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
