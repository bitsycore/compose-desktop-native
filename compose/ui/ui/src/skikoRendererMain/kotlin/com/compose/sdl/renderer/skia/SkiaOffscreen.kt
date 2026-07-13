package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.compose.sdl.graphics.EncodedImageDecoder
import com.compose.sdl.graphics.OffscreenRenderer
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

// ==================
// MARK: Skia offscreen bitmap support
// ==================

// An ImageBitmap backed by a Skia Surface. The vector rasterises INTO the
// surface (via a Canvas returned by the factory) and SkiaCanvas.drawImageRect
// blits it back — with an Icon's tint applied through the paint's ColorFilter.
// prepareToDraw() invalidates the cached snapshot so the next blit re-samples
// the freshly-drawn surface.
internal class SkiaImageBitmap(
	override val width: Int,
	override val height: Int,
	override val config: ImageBitmapConfig,
	override val hasAlpha: Boolean,
	override val colorSpace: ColorSpace,
) : ImageBitmap {

	// Raster (CPU) surface: matches SkiaImageCache.rasterizeSvg's pattern and
	// works on every SkiaBridge (Metal / OpenGL / software raster). Icon-sized
	// vectors are tiny; a GPU surface would need a shared GrContext with the
	// active bridge which Skiko doesn't expose portably here.
	val surface: Surface = Surface.makeRasterN32Premul(width.coerceAtLeast(1), height.coerceAtLeast(1))

	// Cached snapshot of the surface pixels. Cleared by prepareToDraw() so a
	// re-rasterised vector reuploads on the next blit; retained across frames
	// when the vector doesn't change.
	private var fSnapshot: Image? = null

	fun snapshot(): Image {
		val vExisting = fSnapshot
		if (vExisting != null) return vExisting
		val vFresh = surface.makeImageSnapshot()
		fSnapshot = vFresh
		return vFresh
	}

	override fun readPixels(
		buffer: IntArray,
		startX: Int,
		startY: Int,
		width: Int,
		height: Int,
		bufferOffset: Int,
		stride: Int,
	) {
	}

	override fun prepareToDraw() {
		fSnapshot?.close()
		fSnapshot = null
	}
}

// Registered on SkiaRenderBackend; hands the vendored DrawCache a real offscreen.
internal class SkiaOffscreenRenderer(
	private val fTextRenderer: SkiaTextRenderer?,
	private val fImageCache: SkiaImageCache?,
) : OffscreenRenderer {

	override fun createImageBitmap(
		width: Int,
		height: Int,
		config: ImageBitmapConfig,
		hasAlpha: Boolean,
		colorSpace: ColorSpace,
	): ImageBitmap = SkiaImageBitmap(width, height, config, hasAlpha, colorSpace)

	override fun createCanvas(image: ImageBitmap): Canvas? {
		val vBmp = image as? SkiaImageBitmap ?: return null
		return SkiaCanvas(
			vBmp.surface.canvas,
			Size(vBmp.width.toFloat(), vBmp.height.toFloat()),
			fTextRenderer,
			fImageCache,
		)
	}
}

// ==================
// MARK: SkiaEncodedImageDecoder
// ==================

/* Encoded-image decode hook impl for the Skia renderer — the counterpart of
   Sdl3EncodedImageDecoder, consumed by :components-resources' actuals
   (painterResource / SVG). Raster formats go through Image.makeFromEncoded;
   when that fails the bytes are retried as SVG through the same SVGDOM
   rasterisation SkiaImageCache uses. Pure CPU raster (no GrContext), so it is
   safe on the resources pipeline's Dispatchers.Default workers. */
internal class SkiaEncodedImageDecoder : EncodedImageDecoder {

	override fun decode(inBytes: ByteArray): ImageBitmap? {
		if (inBytes.isEmpty()) return null
		val vImg = runCatching { Image.makeFromEncoded(inBytes) }.getOrNull()
			?: rasterizeSvg(inBytes)
			?: return null
		val vBmp = SkiaImageBitmap(
			vImg.width, vImg.height,
			config = ImageBitmapConfig.Argb8888,
			hasAlpha = true,
			colorSpace = ColorSpaces.Srgb,
		)
		vBmp.surface.canvas.drawImage(vImg, 0f, 0f)
		vImg.close()
		return vBmp
	}
}
