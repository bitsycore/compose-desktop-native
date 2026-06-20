package com.compose.desktop.native.renderer.skia

import com.compose.desktop.native.*

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.AndroidVectorToSvg
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: SkiaImageCache
// ==================

/* Decodes bundled image resources to org.jetbrains.skia.Image and caches them
   by relative path; backs both the layout pass (intrinsicSize) and the
   renderer's draw. Raster formats go through Image.makeFromEncoded; SVG and
   the SVG produced from Android <vector> XML are rendered through SVGDOM into
   an offscreen surface and snapshotted to an Image, so the draw path is the
   same drawImageRect for every kind.

   NOTE: this source set isn't compiled on the mingwX64 host, so it is built
   only on macOS / Linux — keep it to the Skiko APIs already used elsewhere in
   this module. */
internal class SkiaImageCache {

	// Value is null when a decode failed — cached to avoid retrying each frame.
	private val fCache = HashMap<String, Image?>()

	fun intrinsicSize(inPath: String, inKind: ResourceKind): IntSize {
		val vImg = get(inPath, inKind) ?: return IntSize(-1, -1)
		return IntSize(vImg.width, vImg.height)
	}

	private fun get(inPath: String, inKind: ResourceKind): Image? {
		if (fCache.containsKey(inPath)) return fCache[inPath]
		val vImage = decode(inPath, inKind)
		fCache[inPath] = vImage
		return vImage
	}

	private fun decode(inPath: String, inKind: ResourceKind): Image? {
		val vBytes = loadComposeResourceBytes(inPath) ?: return null
		return when (inKind) {
			ResourceKind.Raster        -> runCatching { Image.makeFromEncoded(vBytes) }.getOrNull()
			ResourceKind.Svg           -> rasterizeSvg(vBytes)
			ResourceKind.AndroidVector -> rasterizeSvg(AndroidVectorToSvg.convert(vBytes.decodeToString()).encodeToByteArray())
			ResourceKind.Raw           -> null
		}
	}

	/* SVGDOM → offscreen raster → Image. Falls back to a 100×100 canvas when
	   the document declares no explicit width/height (the Android-vector path
	   always supplies them). */
	private fun rasterizeSvg(inBytes: ByteArray): Image? = runCatching {
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

	// ==================
	// MARK: Draw
	// ==================

	/* Paints the resource into (inX, inY, inW, inH) applying contentScale +
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
		inScale: ContentScale,
		inAlpha: Float,
	) {
		if (inW <= 0f || inH <= 0f) return
		val vImg = get(inPath, inKind) ?: return
		val vIw = vImg.width.toFloat()
		val vIh = vImg.height.toFloat()
		if (vIw <= 0f || vIh <= 0f) return

		val vPaint = Paint()
		vPaint.color = Color.makeARGB((inAlpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255)

		when (inScale) {
			ContentScale.FillBounds -> {
				inCanvas.drawImageRect(vImg, Rect.makeWH(vIw, vIh), Rect.makeXYWH(inX, inY, inW, inH), vPaint)
			}
			ContentScale.Crop -> {
				val vScale = max(inW / vIw, inH / vIh)
				val vSrcW = inW / vScale
				val vSrcH = inH / vScale
				val vSrc = Rect.makeXYWH((vIw - vSrcW) / 2f, (vIh - vSrcH) / 2f, vSrcW, vSrcH)
				inCanvas.drawImageRect(vImg, vSrc, Rect.makeXYWH(inX, inY, inW, inH), vPaint)
			}
			else -> {
				val vScale = when (inScale) {
					ContentScale.Fit    -> min(inW / vIw, inH / vIh)
					ContentScale.Inside -> min(1f, min(inW / vIw, inH / vIh))
					else                -> 1f   // None
				}
				val vDw = vIw * vScale
				val vDh = vIh * vScale
				val vDx = inX + (inW - vDw) / 2f
				val vDy = inY + (inH - vDh) / 2f
				inCanvas.drawImageRect(vImg, Rect.makeWH(vIw, vIh), Rect.makeXYWH(vDx, vDy, vDw, vDh), vPaint)
			}
		}
		vPaint.close()
	}

	fun destroy() {
		for (vImg in fCache.values) vImg?.close()
		fCache.clear()
	}
}
