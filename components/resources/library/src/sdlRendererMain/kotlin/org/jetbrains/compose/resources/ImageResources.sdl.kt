@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import com.compose.sdl.graphics.decodeEncodedImageBitmap

// ==================
// MARK: Image actuals — SDL renderer
// ==================

/* Decoding goes through the :ui hook the SDL backend registers at init
   (SDL3_image, IMG auto-detect). resourceDensity/targetDensity are ignored:
   under this port's Option-B density flow layout runs in physical pixels and
   drawables ship at a single density, so no decode-time rescale applies. */
internal actual fun ByteArray.toImageBitmap(resourceDensity: Int, targetDensity: Int): ImageBitmap =
	decodeEncodedImageBitmap(this)
		?: error("Image decode failed — is the SDL renderer initialised before painterResource ran?")

/* SVG on SDL: SDL3_image rasterises SVG at its intrinsic size (IMG_LoadSVG),
   so the "element" is just the raw bytes and the painter is the rasterised
   bitmap. No DOM, no vector scaling — prefer XML vector drawables for
   resolution-independent art on this renderer. */
internal actual class SvgElement(val bytes: ByteArray)

internal actual fun ByteArray.toSvgElement(): SvgElement = SvgElement(this)

internal actual fun SvgElement.toSvgPainter(density: Density): Painter =
	BitmapPainter(
		decodeEncodedImageBitmap(bytes)
			?: error("SVG rasterisation failed — is the SDL renderer initialised before painterResource ran?")
	)
