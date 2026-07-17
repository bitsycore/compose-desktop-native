package com.compose.sdl.graphics

import androidx.compose.ui.graphics.GraphicsContext

// ==================
// MARK: GraphicsContext factory seam (B2 / P1.3)
// ==================

/* ComposeOwner.graphicsContext is created behind a per-renderer factory seam so each
   leg supplies its own GraphicsContext: exactly one actual is attached per target
   (the createRenderBackend trick). SDL → the project ProjectGraphicsContext (record/
   replay via the SDL NativeRenderNode); Skia → upstream's SkiaGraphicsContext (owns a
   skiko RenderNodeContext). See RENDERER_CONVERGE.md §4 (B2). */
internal expect fun createGraphicsContext(): GraphicsContext
