package sdl3backend

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.text.TextMeasurer

// ==================
// MARK: RenderBackend
// ==================

/* Per-frame rendering interface that ComposeWindow uses. Hides whether
   we're drawing through Skia (macOS / Linux) or pure SDL3 primitives
   (mingwX64). Each implementation owns its own measurement strategy
   exposed via textMeasurer so the common layout pass agrees with what
   the renderer paints. */
interface RenderBackend {
    /* Measurer the shared TextMeasurePolicy plugs into to keep layout
       widths in sync with what the renderer actually draws. */
    val textMeasurer: TextMeasurer

    /* Re-allocate / resize anything that depends on the back buffer
       dimensions. Width / height are in PHYSICAL PIXELS (HiDPI-aware).
       Returns false if the surface couldn't be (re)created. */
    fun ensureSize(inPixelWidth: Int, inPixelHeight: Int): Boolean

    /* Prepare for a new frame. dpr scales Compose's logical-point layout
       so it maps 1:1 onto the pixel back buffer. */
    fun beginFrame(inDpr: Float)

    /* Walk the LayoutNode tree and draw each node. */
    fun draw(inRoot: LayoutNode)

    /* Flush + present whatever was just drawn. */
    fun endFrame()

    /* Read back the current frame to host memory as (w, h, BGRA bytes),
       or null if unavailable. Used by the demo's --screenshot. */
    fun snapshotBgra(): Triple<Int, Int, ByteArray>?

    /* Release resources. */
    fun destroy()
}

// ==================
// MARK: Factory (per-target)
// ==================

/* Each native target picks the renderer it can support:
   - skia (macOS / Linux): SkiaRenderBackend (CPU raster / OpenGL / Metal)
   - mingwX64: Sdl3RenderBackend (SDL3 primitives + SDL3_ttf, no GPU Skia)
   Returns null if the requested GPU mode can't be initialised. */
internal expect fun makeRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend?
