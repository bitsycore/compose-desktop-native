package com.compose.sdl.renderer.skia

// ==================
// MARK: SkiaLeafDrawer — port text/image renderers behind the upstream canvas (B6.1)
// ==================

/* The Skia leg draws through upstream's `SkiaBackedCanvas` (real gradients/paint/
   shader), but keeps the port's text engine (`SkiaTextRenderer`, incl. the P3.1
   metric work) and resource-image cache (`SkiaImageCache`). `SkiaBackedCanvas` is
   manual-vendored to implement the port `NativeTextCanvas`/`NativePainterCanvas`
   contracts and forwards to this drawer, which holds the two long-lived renderers
   the way the old per-frame `SkiaCanvas` used to. Set once by `SkiaRenderBackend`;
   any `SkiaBackedCanvas` (frame, offscreen, or a GraphicsLayer recording — B6.2)
   can then draw text/images onto its own `internalSkiaCanvas`. */
internal var skiaLeafDrawer: SkiaLeafDrawer? = null

internal class SkiaLeafDrawer(
	val textRenderer: SkiaTextRenderer,
	val imageCache: SkiaImageCache,
)
