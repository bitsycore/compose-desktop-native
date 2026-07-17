package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.Size
import com.compose.sdl.res.ImageLoader
import com.compose.sdl.res.ResourceKind
import com.compose.sdl.text.TextMeasurer
import com.compose.sdl.*
import androidx.compose.ui.graphics.asComposeCanvas
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

   B6.1: the composition draws through upstream's SkiaBackedCanvas (real
   paint/shader/gradients). The port's text engine (SkiaTextRenderer) +
   resource-image cache (SkiaImageCache) + elevation shadows ride behind it
   via skiaLeafDrawer + the SkiaBackedCanvas manual-vendor. */
internal class SkiaRenderBackend(
    private val sdl: SDL3Backend,
    private val gpuMode: GpuMode,
) : RenderBackend {

    private val fBridge: SkiaBridge = buildBridge()
    private val fSkiaTextRenderer = SkiaTextRenderer()
    private val fSkiaImageCache = SkiaImageCache()
    private var fCurrentCanvas: Canvas? = null

    init {
        // Encoded-image decode (painterResource / SVG in :components-resources).
        // Registered at CONSTRUCTION, not first frame: the official resources
        // pipeline decodes during COMPOSITION, which runs before beginFrame —
        // and on Dispatchers.Default workers, which the CPU-raster Skia decoder
        // tolerates. Without this the first painterResource on a Skia build
        // dies with "Image decode failed" (issue #1).
        if (com.compose.sdl.graphics.encodedImageDecoder == null) {
            com.compose.sdl.graphics.encodedImageDecoder = SkiaEncodedImageDecoder()
        }
    }

    // B6.1: the Skia leg draws through upstream SkiaBackedCanvas; its port draw
    // contracts (text/painter/shadow) forward to the port renderers via this global
    // drawer. It is per-WINDOW state (each window has its own text renderer + image
    // cache), so it must be re-pointed at THIS backend before every frame — exactly
    // like ComposeWindow.installGlobals() does for currentTextMeasurer. Setting it
    // once in the ctor left it dangling at a CLOSED window's (destroyed) renderer
    // after a multi-window teardown → crash on the surviving window's next frame.
    private val fLeafDrawer = SkiaLeafDrawer(fSkiaTextRenderer, fSkiaImageCache)

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
        // buffer shows uninitialized GPU memory (pink). Clearing before scale
        // so it covers the whole physical target.
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

    override fun drawRoot(inDraw: (androidx.compose.ui.graphics.Canvas) -> Unit) {
        val canvas = fCurrentCanvas ?: return
        // Point the leaf-draw global at THIS window's renderers before drawing.
        skiaLeafDrawer = fLeafDrawer
        // Wrap the live skia canvas as upstream's SkiaBackedCanvas (real gradients/
        // paint/shader). Its port draw contracts forward to skiaLeafDrawer. The
        // ImageBitmap-backed offscreen (VectorPainter / DrawCache) now goes through
        // upstream's own ActualCanvas, so no project offscreenRenderer registration.
        val vCanvas = canvas.asComposeCanvas()
        inDraw(vCanvas)
        (vCanvas as? com.compose.sdl.graphics.NativeFinishableCanvas)?.finish()
    }

    override fun endFrame() {
        fCurrentCanvas?.restore()
        fCurrentCanvas = null
        fBridge.present()
    }

    override fun snapshotBgra(): Triple<Int, Int, ByteArray>? = fBridge.snapshotBgra()

    override fun destroy() {
        // Drop the global if it still points at this (about-to-be-destroyed) backend,
        // so a stray draw before the next window's drawRoot can't hit freed renderers.
        if (skiaLeafDrawer === fLeafDrawer) skiaLeafDrawer = null
        fSkiaImageCache.destroy()
        fSkiaTextRenderer.destroy()
        fBridge.destroy()
    }
}
