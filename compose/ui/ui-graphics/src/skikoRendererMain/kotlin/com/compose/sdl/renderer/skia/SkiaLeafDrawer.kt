package com.compose.sdl.renderer.skia

// ==================
// MARK: SkiaLeafDrawer - port text/image renderers behind the upstream canvas (B6.1)
// ==================

/** The Skia leg draws through upstream's `SkiaBackedCanvas` (real gradients/paint/
   shader). Text now goes through the skiko paragraph engine (Paragraph.paint), so
   this drawer only holds the resource-image cache (`SkiaImageCache`) that
   `SkiaBackedCanvas`'s `NativePainterCanvas` contract forwards to. Set once by
   `SkiaRenderBackend`; any `SkiaBackedCanvas` (frame, offscreen, or a GraphicsLayer
   recording - B6.2) can then draw images onto its own `internalSkiaCanvas`. */
var skiaLeafDrawer: SkiaLeafDrawer? = null

val defaultSkiaLeafDrawer: SkiaLeafDrawer by lazy {
	if (com.compose.sdl.graphics.encodedImageDecoder == null) {
		com.compose.sdl.graphics.encodedImageDecoder = SkiaEncodedImageDecoder()
	}
	val cache = SkiaImageCache()
	com.compose.sdl.res.currentImageLoader = object : com.compose.sdl.res.ImageLoader {
		override fun intrinsicSize(path: String, kind: com.compose.sdl.res.ResourceKind): androidx.compose.ui.geometry.Size =
			cache.intrinsicSize(path, kind)
		override fun readBytes(path: String): ByteArray? =
			com.compose.sdl.res.composeResourceReader?.invoke(path)
	}
	SkiaLeafDrawer(cache)
}

class SkiaLeafDrawer(
	val imageCache: SkiaImageCache,
)
