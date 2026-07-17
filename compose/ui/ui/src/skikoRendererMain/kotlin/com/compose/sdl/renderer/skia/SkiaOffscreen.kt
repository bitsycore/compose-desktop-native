package com.compose.sdl.renderer.skia

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.compose.sdl.graphics.EncodedImageDecoder
import org.jetbrains.skia.Image

// ==================
// MARK: SkiaEncodedImageDecoder
// ==================

/* Encoded-image decode hook impl for the Skia renderer — the counterpart of
   Sdl3EncodedImageDecoder, consumed by :components-resources' actuals
   (painterResource / SVG). Raster formats go through Image.makeFromEncoded;
   when that fails the bytes are retried as SVG through the same SVGDOM
   rasterisation SkiaImageCache uses. Pure CPU raster (no GrContext), so it is
   safe on the resources pipeline's Dispatchers.Default workers.

   B6.1: produces an UPSTREAM ImageBitmap (Image.toComposeImageBitmap) — the Skia
   leg now uses upstream's SkiaBackedCanvas/SkiaImageAsset, so the offscreen +
   ImageBitmap-backed paths go through upstream's own actuals (the project
   SkiaImageBitmap / SkiaOffscreenRenderer are retired). */
internal class SkiaEncodedImageDecoder : EncodedImageDecoder {

	override fun decode(inBytes: ByteArray): ImageBitmap? {
		if (inBytes.isEmpty()) return null
		val vImg = runCatching { Image.makeFromEncoded(inBytes) }.getOrNull()
			?: rasterizeSvg(inBytes)
			?: return null
		val vBmp = vImg.toComposeImageBitmap()
		vImg.close()
		return vBmp
	}
}
