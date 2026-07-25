package com.compose.sdl.graphics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces

// ==================
// MARK: ProjectImageBitmap
// ==================

/**
 * Stub concrete impl of the upstream [ImageBitmap] interface. Our image
 * pipeline goes through Painter + per-backend caches (SkiaImageCache /
 * Sdl3ImageCache), not ImageBitmap. This class lets the vendored
 * `ImageBitmap(width, height, ...)` factory return a non-null value so
 * upstream-shaped consumers compile; readPixels / prepareToDraw are no-ops.
 */
class ProjectImageBitmap(
	override val width: Int,
	override val height: Int,
	override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888,
	override val hasAlpha: Boolean = true,
	override val colorSpace: ColorSpace = ColorSpaces.Srgb,
) : ImageBitmap {

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

	override fun prepareToDraw() {}
}
