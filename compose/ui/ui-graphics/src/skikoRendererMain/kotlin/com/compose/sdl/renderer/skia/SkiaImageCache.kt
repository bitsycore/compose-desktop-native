package com.compose.sdl.renderer.skia

import androidx.compose.ui.geometry.isSpecified
import com.compose.sdl.res.AndroidVectorToSvg
import com.compose.sdl.res.ResourceKind
import com.compose.sdl.res.composeResourceReader
import kotlin.math.roundToInt
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

	// SVG / Android-vector kinds are RESOLUTION-INDEPENDENT: instead of caching one
	// intrinsic-size raster and letting drawImageRect upscale it (blurry when an icon
	// is drawn larger than its intrinsic box), we re-rasterise the vector at the
	// destination pixel size and cache that raster keyed by "path@WxH". Mirrors
	// upstream's size-driven DrawCache for SVGPainter. Intrinsic dims are cached
	// separately so the layout pass (intrinsicSize) doesn't force a raster.
	private val fSvgRasterCache = LinkedHashMap<String, Image?>()
	private val fSvgIntrinsic = HashMap<String, androidx.compose.ui.geometry.Size>()

	fun intrinsicSize(inPath: String, inKind: ResourceKind): androidx.compose.ui.geometry.Size {
		if (inKind == ResourceKind.Svg || inKind == ResourceKind.AndroidVector) {
			return svgIntrinsicSize(inPath, inKind)
		}
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
		if (inKind == ResourceKind.Svg || inKind == ResourceKind.AndroidVector) {
			drawSvg(inCanvas, inPath, inKind, inX, inY, inW, inH, inAlpha)
			return
		}
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

	// ==================
	// MARK: Size-driven SVG / Android-vector
	// ==================

	/** Raw SVG bytes for the resource (Android-vector XML is converted first). */
	private fun svgBytes(inPath: String, inKind: ResourceKind): ByteArray? {
		val vRaw = composeResourceReader?.invoke(inPath) ?: return null
		return if (inKind == ResourceKind.AndroidVector) {
			runCatching { AndroidVectorToSvg.convert(vRaw.decodeToString()).encodeToByteArray() }.getOrNull()
		} else {
			vRaw
		}
	}

	/** Intrinsic (viewport) size of the vector, parsed once and cached. */
	private fun svgIntrinsicSize(inPath: String, inKind: ResourceKind): androidx.compose.ui.geometry.Size {
		fSvgIntrinsic[inPath]?.let { return it }
		val vBytes = svgBytes(inPath, inKind) ?: return androidx.compose.ui.geometry.Size.Unspecified
		val vSize = runCatching {
			val vDom = SVGDOM(Data.makeFromBytes(vBytes))
			val vRoot = vDom.root
			val vW = (vRoot?.width?.value ?: 0f).takeIf { it > 0f } ?: 100f
			val vH = (vRoot?.height?.value ?: 0f).takeIf { it > 0f } ?: 100f
			vDom.close()
			androidx.compose.ui.geometry.Size(vW, vH)
		}.getOrDefault(androidx.compose.ui.geometry.Size.Unspecified)
		fSvgIntrinsic[inPath] = vSize
		return vSize
	}

	private fun drawSvg(
		inCanvas: Canvas, inPath: String, inKind: ResourceKind,
		inX: Float, inY: Float, inW: Float, inH: Float, inAlpha: Float,
	) {
		val vWpx = inW.roundToInt().coerceAtLeast(1)
		val vHpx = inH.roundToInt().coerceAtLeast(1)
		val vKey = "$inPath@${vWpx}x$vHpx"
		val vImg = if (fSvgRasterCache.containsKey(vKey)) {
			val vExisting = fSvgRasterCache.remove(vKey)
			fSvgRasterCache[vKey] = vExisting
			vExisting
		} else {
			val vIntrinsic = svgIntrinsicSize(inPath, inKind)
			val vBytes = svgBytes(inPath, inKind)
			val vRaster = if (vBytes != null && vIntrinsic.isSpecified) {
				rasterizeSvgAt(vBytes, vIntrinsic.width, vIntrinsic.height, vWpx, vHpx)
			} else null
			fSvgRasterCache[vKey] = vRaster
			if (fSvgRasterCache.size > MAX_CACHED_IMAGES) {
				fSvgRasterCache.keys.firstOrNull()?.let { fSvgRasterCache.remove(it)?.close() }
			}
			vRaster
		} ?: return

		val vPaint = Paint()
		vPaint.color = Color.makeARGB((inAlpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
		// Raster is already at destination pixel size → 1:1 blit (crisp, no upscale).
		inCanvas.drawImageRect(vImg, Rect.makeWH(vImg.width.toFloat(), vImg.height.toFloat()),
			Rect.makeXYWH(inX, inY, inW, inH), vPaint)
		vPaint.close()
	}

	fun destroy() {
		for (vImg in fCache.values) vImg?.close()
		fCache.clear()
		for (vImg in fSvgRasterCache.values) vImg?.close()
		fSvgRasterCache.clear()
		fSvgIntrinsic.clear()
	}
}

// ==================
// MARK: SVG rasterisation (shared with SkiaEncodedImageDecoder)
// ==================

/** SVGDOM → offscreen raster → Image. Falls back to a 100×100 canvas when
   the document declares no explicit width/height (the Android-vector path
   always supplies them). */
/** Size-driven SVG raster: renders the vector into a [inWpx]×[inHpx] surface by
   scaling the intrinsic-sized document up to the target, so a vector drawn larger
   than its intrinsic box stays crisp (vector render at target resolution) instead
   of upscaling a small raster. Canvas-scale (rather than only setContainerSize)
   keeps it correct whether or not the document declares a viewBox. */
internal fun rasterizeSvgAt(inBytes: ByteArray, inIntrinsicW: Float, inIntrinsicH: Float, inWpx: Int, inHpx: Int): Image? = runCatching {
	val vDom = SVGDOM(Data.makeFromBytes(inBytes))
	val vIw = inIntrinsicW.takeIf { it > 0f } ?: 100f
	val vIh = inIntrinsicH.takeIf { it > 0f } ?: 100f
	vDom.setContainerSize(vIw, vIh)
	val vSurface = Surface.makeRasterN32Premul(inWpx, inHpx)
	val vCanvas = vSurface.canvas
	vCanvas.scale(inWpx / vIw, inHpx / vIh)
	vDom.render(vCanvas)
	val vImage = vSurface.makeImageSnapshot()
	vDom.close()
	vSurface.close()
	vImage
}.getOrNull()

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
