package com.compose.sdl

import com.compose.sdl.res.ImageLoader
import com.compose.sdl.text.TextMeasurer

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

    /* Loader the shared ImageMeasurePolicy / Res.readBytes plug into. Backed
       by the same decode cache the renderer uses to paint images, so a
       resource is decoded once and shared between measure and draw. */
    val imageLoader: ImageLoader

    /* Re-allocate / resize anything that depends on the back buffer
       dimensions. Width / height are in PHYSICAL PIXELS (HiDPI-aware).
       Returns false if the surface couldn't be (re)created. */
    fun ensureSize(inPixelWidth: Int, inPixelHeight: Int): Boolean

    /* Prepare for a new frame. dpr scales Compose's logical-point layout
       so it maps 1:1 onto the pixel back buffer. */
    fun beginFrame(inDpr: Float)

    /* Build this frame's platform Canvas and hand it to [inDraw], which walks the
       composition (host.drawRoot(canvas)) through the vendored coordinator /
       DrawModifierNode pipeline. Taking a (Canvas)->Unit instead of the host keeps
       RenderBackend + its implementations independent of the node/host layer — the
       decoupling that lets the renderers live in :ui-graphics (no ui-graphics→ui
       cycle). Default no-op. */
    fun drawRoot(inDraw: (canvas: androidx.compose.ui.graphics.Canvas) -> Unit) {}

    /* Flush + present whatever was just drawn. */
    fun endFrame()

    /* Read back the current frame to host memory as (w, h, BGRA bytes),
       or null if unavailable. Used by the demo's --screenshot. */
    fun snapshotBgra(): Triple<Int, Int, ByteArray>?

    /* Release resources. */
    fun destroy()
}

// The per-target factory now lives in :window (an expect that delegates to
// the selected renderer module's createRenderBackend). Renderer modules
// expose createRenderBackend(...) / rendererPreferredGpuMode() in this same
// package; the build includes exactly one of them per target.
