package com.compose.sdl.graphics

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.SkiaGraphicsContext

// ==================
// MARK: createGraphicsContext — Skia renderer actual (B6.2)
// ==================

/* The Skia leg uses upstream's SkiaGraphicsContext, which owns a skiko
   RenderNodeContext and creates upstream GraphicsLayer(skiko.RenderNode) instances.
   See RENDERER_TASKS.md B6.2 + RENDERER_CONVERGE.md §4 (B2). */
internal actual fun createGraphicsContext(): GraphicsContext = SkiaGraphicsContext()
