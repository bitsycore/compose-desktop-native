package com.compose.sdl.graphics

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

// ==================
// MARK: Encoded-image decode hook (renderer-provided)
// ==================

/** Decodes an ENCODED image (png / jpg / webp / bmp / gif / svg bytes) into a
   drawable [ImageBitmap]. The active renderer registers its implementation at
   backend construction — the Skia backend goes through Image.makeFromEncoded /
   SVGDOM (SkiaEncodedImageDecoder). :components-resources' actuals share this
   hook.

   Consumed by `ByteArray.toImageBitmap` / `SvgElement.toSvgPainter` so
   painterResource(Res.drawable.x) works without that module reaching into
   renderer internals. */
interface EncodedImageDecoder {
	fun decode(inBytes: ByteArray): ImageBitmap?

	/** Intrinsic (viewport) size of an SVG document, or null if it can't be
	   parsed. Used by the resolution-independent SvgPainter for layout. */
	fun svgIntrinsicSize(inBytes: ByteArray): Size? = null

	/** Rasterise an SVG at a specific PIXEL size (resolution-independent draw).
	   Default falls back to the intrinsic-size [decode] for impls that don't
	   support size-driven rendering. */
	fun decodeSvgAt(inBytes: ByteArray, inWidthPx: Int, inHeightPx: Int): ImageBitmap? = decode(inBytes)
}

/** Volatile: written by the render backend on the main thread, read by the
   resources pipeline on Dispatchers.Default workers. */
@kotlin.concurrent.Volatile
var encodedImageDecoder: EncodedImageDecoder? = null

/** Decode via the active renderer's registered decoder — null when no renderer
 *  has initialised yet or the bytes aren't a supported image. */
fun decodeEncodedImageBitmap(inBytes: ByteArray): ImageBitmap? =
	encodedImageDecoder?.decode(inBytes)

/** Intrinsic size of an SVG document via the active decoder, or null. */
fun svgIntrinsicSize(inBytes: ByteArray): Size? =
	encodedImageDecoder?.svgIntrinsicSize(inBytes)

/** Rasterise an SVG at [inWidthPx]×[inHeightPx] via the active decoder — the
 *  resolution-independent draw path used by the resources SvgPainter. */
fun decodeSvgAt(inBytes: ByteArray, inWidthPx: Int, inHeightPx: Int): ImageBitmap? =
	encodedImageDecoder?.decodeSvgAt(inBytes, inWidthPx, inHeightPx)
