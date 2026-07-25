package com.compose.sdl.renderer.skia

// ==================
// MARK: SkiaLeafDrawer — port text/image renderers behind the upstream canvas (B6.1)
// ==================

/** The Skia leg draws through upstream's `SkiaBackedCanvas` (real gradients/paint/
   shader). Text now goes through the skiko paragraph engine (Paragraph.paint), so
   this drawer only holds the resource-image cache (`SkiaImageCache`) that
   `SkiaBackedCanvas`'s `NativePainterCanvas` contract forwards to. Set once by
   `SkiaRenderBackend`; any `SkiaBackedCanvas` (frame, offscreen, or a GraphicsLayer
   recording — B6.2) can then draw images onto its own `internalSkiaCanvas`. */
internal var skiaLeafDrawer: SkiaLeafDrawer? = null

internal class SkiaLeafDrawer(
	val imageCache: SkiaImageCache,
)
