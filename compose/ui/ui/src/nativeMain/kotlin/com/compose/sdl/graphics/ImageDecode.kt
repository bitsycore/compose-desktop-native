package com.compose.sdl.graphics

import androidx.compose.ui.graphics.ImageBitmap

// ==================
// MARK: Encoded-image decode hook (renderer-provided)
// ==================

/* Decodes an ENCODED image (png / jpg / webp / bmp / gif / svg bytes) into a
   drawable [ImageBitmap]. The active renderer registers its implementation —
   the SDL backend goes through SDL3_image (see Sdl3EncodedImageDecoder); the
   Skia renderer never registers because its resources pipeline decodes through
   Skia directly (vendored skikoRenderer actuals in :components-resources).

   Consumed by :components-resources' SDL actuals (`ByteArray.toImageBitmap`,
   `SvgElement.toSvgPainter`) so painterResource(Res.drawable.x) works without
   that module reaching into renderer internals. */
interface EncodedImageDecoder {
	fun decode(inBytes: ByteArray): ImageBitmap?
}

var encodedImageDecoder: EncodedImageDecoder? = null
	internal set

/** Decode via the active renderer's registered decoder — null when no renderer
 *  has initialised yet or the bytes aren't a supported image. */
fun decodeEncodedImageBitmap(inBytes: ByteArray): ImageBitmap? =
	encodedImageDecoder?.decode(inBytes)
