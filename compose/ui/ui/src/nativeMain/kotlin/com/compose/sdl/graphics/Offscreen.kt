package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace

// ==================
// MARK: Offscreen bitmap support (renderer-provided)
// ==================

// The vendored VectorPainter / DrawCache render an ImageVector by rasterising it
// into an offscreen ImageBitmap (via Canvas(imageBitmap)) and blitting that back
// with the tint applied as a ColorFilter. That's how every material3-internal icon
// draws (the SegmentedButton checkmark, ExposedDropdownMenu / DatePicker arrows,
// Snackbar close, …). It needs a real offscreen raster.
//
// A renderer registers its implementation here; the ActualImageBitmap / ActualCanvas
// factories delegate to it. When none is registered (e.g. the Skia stub path) the
// ProjectImageBitmap / ProjectCanvas placeholders are used and vector icons don't
// render — same as before. This keeps the capability in the renderer instead of
// forking the vendored Compose vector pipeline.
interface OffscreenRenderer {
	// Backing bitmap the vector rasterises into. Null → fall back to the stub.
	fun createImageBitmap(
		width: Int,
		height: Int,
		config: ImageBitmapConfig,
		hasAlpha: Boolean,
		colorSpace: ColorSpace,
	): ImageBitmap?

	// A Canvas that draws into [image]'s backing. Null → fall back to the stub.
	fun createCanvas(image: ImageBitmap): Canvas?
}

// Set once by the active renderer (see the SDL renderer's Sdl3OffscreenRenderer).
internal var offscreenRenderer: OffscreenRenderer? = null
