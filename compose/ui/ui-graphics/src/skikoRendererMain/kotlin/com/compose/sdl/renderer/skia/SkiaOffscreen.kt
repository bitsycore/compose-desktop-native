package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.compose.sdl.graphics.EncodedImageDecoder
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.svg.SVGDOM

// ==================
// MARK: SkiaEncodedImageDecoder
// ==================

/** Encoded-image decode hook impl for the Skia renderer, consumed by
   :components-resources' actuals (painterResource / SVG). Raster formats go
   through Image.makeFromEncoded; when that fails the bytes are retried as SVG
   through the same SVGDOM rasterisation SkiaImageCache uses. Pure CPU raster
   (no GrContext), so it is safe on the resources pipeline's Dispatchers.Default
   workers.

   B6.1: produces an UPSTREAM ImageBitmap (Image.toComposeImageBitmap) — the Skia
   renderer uses upstream's SkiaBackedCanvas/SkiaImageAsset, so the offscreen +
   ImageBitmap-backed paths go through upstream's own actuals (the project
   SkiaImageBitmap / SkiaOffscreenRenderer are retired). */
class SkiaEncodedImageDecoder : EncodedImageDecoder {

	override fun decode(inBytes: ByteArray): ImageBitmap? {
		if (inBytes.isEmpty()) return null
		val vImg = runCatching { Image.makeFromEncoded(inBytes) }.getOrNull()
			?: rasterizeSvg(inBytes)
			?: return null
		val vBmp = vImg.toComposeImageBitmap()
		vImg.close()
		return vBmp
	}

	override fun svgIntrinsicSize(inBytes: ByteArray): Size? = runCatching {
		val vDom = SVGDOM(Data.makeFromBytes(inBytes))
		val vRoot = vDom.root
		val vW = (vRoot?.width?.value ?: 0f).takeIf { it > 0f } ?: 100f
		val vH = (vRoot?.height?.value ?: 0f).takeIf { it > 0f } ?: 100f
		vDom.close()
		Size(vW, vH)
	}.getOrNull()

	override fun decodeSvgAt(inBytes: ByteArray, inWidthPx: Int, inHeightPx: Int): ImageBitmap? {
		if (inBytes.isEmpty() || inWidthPx <= 0 || inHeightPx <= 0) return null
		val vSize = svgIntrinsicSize(inBytes) ?: return null
		val vImg = rasterizeSvgAt(inBytes, vSize.width, vSize.height, inWidthPx, inHeightPx) ?: return null
		val vBmp = vImg.toComposeImageBitmap()
		vImg.close()
		return vBmp
	}
}
