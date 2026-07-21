package com.compose.sdl.graphics

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.SkiaGraphicsContext

// ==================
// MARK: createGraphicsContext — Skia renderer actual (B6.2)
// ==================

/** The Skia leg uses upstream's SkiaGraphicsContext, which owns a skiko
   RenderNodeContext and creates upstream GraphicsLayer(skiko.RenderNode) instances.
   See RENDERER.md (B6.2, section 4). */
internal actual fun createGraphicsContext(): GraphicsContext = SkiaGraphicsContext()
