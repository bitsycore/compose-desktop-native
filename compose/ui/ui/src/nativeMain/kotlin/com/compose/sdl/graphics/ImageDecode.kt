package com.compose.sdl.graphics

import androidx.compose.ui.graphics.ImageBitmap

// ==================
// MARK: Encoded-image decode hook (renderer-provided)
// ==================

/** Decodes an ENCODED image (png / jpg / webp / bmp / gif / svg bytes) into a
   drawable [ImageBitmap]. The active renderer registers its implementation at
   backend construction — the SDL backend goes through SDL3_image
   (Sdl3EncodedImageDecoder), the Skia backend through Image.makeFromEncoded /
   SVGDOM (SkiaEncodedImageDecoder). :components-resources' actuals share this
   hook under BOTH renderers (its skikoRendererMain reuses the SDL actuals).

   Consumed by `ByteArray.toImageBitmap` / `SvgElement.toSvgPainter` so
   painterResource(Res.drawable.x) works without that module reaching into
   renderer internals. */
interface EncodedImageDecoder {
	fun decode(inBytes: ByteArray): ImageBitmap?
}

/** Volatile: written by the render backend on the main thread, read by the
   resources pipeline on Dispatchers.Default workers. */
@kotlin.concurrent.Volatile
var encodedImageDecoder: EncodedImageDecoder? = null
	internal set

/** Decode via the active renderer's registered decoder — null when no renderer
 *  has initialised yet or the bytes aren't a supported image. */
fun decodeEncodedImageBitmap(inBytes: ByteArray): ImageBitmap? =
	encodedImageDecoder?.decode(inBytes)
