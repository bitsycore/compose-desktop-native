@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.compose.sdl.graphics.decodeEncodedImageBitmap
import com.compose.sdl.graphics.decodeSvgAt
import com.compose.sdl.graphics.svgIntrinsicSize
import kotlin.math.roundToInt

// ==================
// MARK: Image actuals — Skia decode via the :ui-graphics hook
// ==================

/** Decoding goes through the :ui-graphics Skia hook the SDL backend registers
   at init (decodeEncodedImageBitmap). resourceDensity/targetDensity are ignored:
   under this port's Option-B density flow layout runs in physical pixels and
   drawables ship at a single density, so no decode-time rescale applies. */
internal actual fun ByteArray.toImageBitmap(resourceDensity: Int, targetDensity: Int): ImageBitmap =
	decodeEncodedImageBitmap(this)
		?: error("Image decode failed — is the render backend initialised before painterResource ran?")

/** SVG element = the raw document bytes; rendering is size-driven (see SvgPainter). */
internal actual class SvgElement(val bytes: ByteArray)

internal actual fun ByteArray.toSvgElement(): SvgElement = SvgElement(this)

/** Resolution-independent SVG painter: reports the document's intrinsic size for
   layout, but RE-RASTERISES the vector at the actual draw size each time the draw
   size changes (1-entry size cache), so a scaled-up SVG stays crisp instead of
   upscaling one intrinsic-size bitmap. Mirrors upstream desktop's SVGPainter. */
internal actual fun SvgElement.toSvgPainter(density: Density): Painter = SvgPainter(bytes)

private class SvgPainter(private val bytes: ByteArray) : Painter() {

	override val intrinsicSize: Size = svgIntrinsicSize(bytes) ?: Size.Unspecified

	private var fAlpha: Float = 1f
	private var fColorFilter: ColorFilter? = null
	private var fCacheKey: Long = -1L
	private var fCacheBitmap: ImageBitmap? = null

	override fun applyAlpha(alpha: Float): Boolean { fAlpha = alpha; return true }
	override fun applyColorFilter(colorFilter: ColorFilter?): Boolean { fColorFilter = colorFilter; return true }

	private fun rasterFor(inWidthPx: Int, inHeightPx: Int): ImageBitmap? {
		val vKey = (inWidthPx.toLong() shl 32) or inHeightPx.toLong()
		if (vKey == fCacheKey && fCacheBitmap != null) return fCacheBitmap
		val vBitmap = decodeSvgAt(bytes, inWidthPx, inHeightPx) ?: return null
		fCacheKey = vKey
		fCacheBitmap = vBitmap
		return vBitmap
	}

	override fun DrawScope.onDraw() {
		val vWpx = size.width.roundToInt()
		val vHpx = size.height.roundToInt()
		if (vWpx <= 0 || vHpx <= 0) return
		val vBitmap = rasterFor(vWpx, vHpx) ?: return
		drawImage(
			vBitmap,
			srcOffset = IntOffset.Zero,
			srcSize = IntSize(vBitmap.width, vBitmap.height),
			dstOffset = IntOffset.Zero,
			dstSize = IntSize(vWpx, vHpx),
			alpha = fAlpha,
			colorFilter = fColorFilter,
		)
	}
}
