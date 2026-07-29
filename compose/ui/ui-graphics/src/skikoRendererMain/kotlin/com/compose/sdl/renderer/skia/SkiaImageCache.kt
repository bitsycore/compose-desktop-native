package com.compose.sdl.renderer.skia

import com.compose.sdl.res.AndroidVectorToSvg
import com.compose.sdl.res.ResourceKind
import com.compose.sdl.res.composeResourceReader
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

// ==================
// MARK: SkiaImageCache
// ==================

/** Decodes bundled image resources to org.jetbrains.skia.Image and caches them
   by relative path; backs both the layout pass (intrinsicSize) and the
   renderer's draw. Raster formats go through Image.makeFromEncoded; SVG and
   the SVG produced from Android <vector> XML are rendered through SVGDOM into
   an offscreen surface and snapshotted to an Image, so the draw path is the
   same drawImageRect for every kind.

   NOTE: this source set isn't compiled on the mingwX64 host, so it is built
   only on macOS / Linux — keep it to the Skiko APIs already used elsewhere in
   this module. */
private const val MAX_CACHED_IMAGES = 256

class SkiaImageCache {

	// Bounded access-order LRU. Value is null when a decode failed — cached to
	// avoid retrying each frame. Bounding + closing the least-recently-used image
	// stops long-running apps that show many distinct runtime images
	// (registerMemoryResource — e.g. downloaded PNGs) from growing image memory
	// without limit (issue #2). On-screen images stay hot; an evicted one just
	// re-decodes on next use.
	private val fCache = LinkedHashMap<String, Image?>()

	fun intrinsicSize(inPath: String, inKind: ResourceKind): androidx.compose.ui.geometry.Size {
		val vImg = get(inPath, inKind) ?: return androidx.compose.ui.geometry.Size.Unspecified
		return androidx.compose.ui.geometry.Size(vImg.width.toFloat(), vImg.height.toFloat())
	}

	private fun get(inPath: String, inKind: ResourceKind): Image? {
		if (fCache.containsKey(inPath)) {
			// Re-insert to mark most-recently-used (LinkedHashMap keeps first =
			// least-recently-used for eviction below).
			val vExisting = fCache.remove(inPath)
			fCache[inPath] = vExisting
			return vExisting
		}
		val vImage = decode(inPath, inKind)
		fCache[inPath] = vImage
		if (fCache.size > MAX_CACHED_IMAGES) {
			val vEldest = fCache.keys.firstOrNull()
			if (vEldest != null) fCache.remove(vEldest)?.close()
		}
		return vImage
	}

	private fun decode(inPath: String, inKind: ResourceKind): Image? {
		val vBytes = composeResourceReader?.invoke(inPath) ?: return null
		return when (inKind) {
			ResourceKind.Raster        -> runCatching { Image.makeFromEncoded(vBytes) }.getOrNull()
			ResourceKind.Svg           -> rasterizeSvg(vBytes)
			ResourceKind.AndroidVector -> rasterizeSvg(AndroidVectorToSvg.convert(vBytes.decodeToString()).encodeToByteArray())
			ResourceKind.Raw           -> null
		}
	}

	// ==================
	// MARK: Draw
	// ==================

	/** Paints the resource into (inX, inY, inW, inH) applying contentScale +
	   alpha. Alpha modulates via the paint colour's alpha channel (RGB is
	   ignored for images without a colour filter). */
	fun draw(
		inCanvas: Canvas,
		inPath: String,
		inKind: ResourceKind,
		inX: Float,
		inY: Float,
		inW: Float,
		inH: Float,
		inAlpha: Float,
	) {
		if (inW <= 0f || inH <= 0f) return
		val vImg = get(inPath, inKind) ?: return
		val vIw = vImg.width.toFloat()
		val vIh = vImg.height.toFloat()
		if (vIw <= 0f || vIh <= 0f) return

		val vPaint = Paint()
		vPaint.color = Color.makeARGB((inAlpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255)

		// Upstream PainterModifier pre-scales the destination per ContentScale, so
		// draw fill-bounds into the given rect.
		inCanvas.drawImageRect(vImg, Rect.makeWH(vIw, vIh), Rect.makeXYWH(inX, inY, inW, inH), vPaint)
		vPaint.close()
	}

	fun destroy() {
		for (vImg in fCache.values) vImg?.close()
		fCache.clear()
	}
}

// ==================
// MARK: SVG rasterisation (shared with SkiaEncodedImageDecoder)
// ==================

/** SVGDOM → offscreen raster → Image. Falls back to a 100×100 canvas when
   the document declares no explicit width/height (the Android-vector path
   always supplies them). */
internal fun rasterizeSvg(inBytes: ByteArray): Image? = runCatching {
	val vDom = SVGDOM(Data.makeFromBytes(inBytes))
	val vRoot = vDom.root
	var vW = vRoot?.width?.value ?: 0f
	var vH = vRoot?.height?.value ?: 0f
	if (vW <= 0f) vW = 100f
	if (vH <= 0f) vH = 100f
	vDom.setContainerSize(vW, vH)
	val vSurface = Surface.makeRasterN32Premul(vW.toInt().coerceAtLeast(1), vH.toInt().coerceAtLeast(1))
	vDom.render(vSurface.canvas)
	vSurface.makeImageSnapshot()
}.getOrNull()
