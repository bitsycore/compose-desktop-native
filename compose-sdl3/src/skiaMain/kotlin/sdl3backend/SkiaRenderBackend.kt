package sdl3backend

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.text.TextMeasurer
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
    private val fSkiaRenderer = SkiaRenderer(fSkiaTextRenderer)
    private var fCurrentCanvas: Canvas? = null

    override val textMeasurer: TextMeasurer
        get() = fSkiaTextRenderer.textMeasurer

    private fun buildBridge(): SkiaBridge = when (gpuMode) {
        GpuMode.METAL  -> makeMetalBridge(sdl) ?: error("Metal bridge unavailable on this target")
        GpuMode.OPENGL -> SkiaGLBridge(sdl).also { require(it.init()) { "SkiaGLBridge.init failed" } }
        GpuMode.NONE   -> SkiaSurfaceBridge(sdl)
        GpuMode.SDL3,
        GpuMode.AUTO   -> error("Only Skia modes are valid here")
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
        fSkiaTextRenderer.destroy()
        fBridge.destroy()
    }
}

// ==================
// MARK: makeRenderBackend (Skia targets)
// ==================

internal actual fun makeRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend? {
    var vResolved = if (inGpu == GpuMode.AUTO) preferredGpuMode() else inGpu
    if (vResolved == GpuMode.SDL3) {
        // SDL3 renderer + SDL3_ttf are only built on mingwX64; on Skia
        // targets we fall back to the Skia CPU bridge so the app still
        // runs instead of erroring out.
        println("Skia target: SDL3 renderer isn't built here — falling back to NONE (CPU raster)")
        vResolved = GpuMode.NONE
    }
    return try {
        SkiaRenderBackend(inSdl, vResolved)
    } catch (t: Throwable) {
        println("makeRenderBackend failed: ${t.message}")
        null
    }
}
