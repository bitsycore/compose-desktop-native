package com.compose.sdl.renderer.sdl

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import com.compose.sdl.graphics.OffscreenRenderer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.reinterpret
import sdl3.*

// ==================
// MARK: SDL offscreen bitmap support
// ==================

// The main-frame Sdl3Canvas currently drawing. An offscreen ImageBitmap render
// (a vector icon) flushes it before switching the render target, so the icon
// composites on top of everything drawn so far (z-order).
@OptIn(ExperimentalForeignApi::class)
internal var currentMainCanvas: Sdl3Canvas? = null

// An ImageBitmap backed by an SDL render-target texture. The vector rasterises
// into the texture (via a Canvas returned by the factory) and Sdl3Canvas.drawImageRect
// blits it back — with the Icon tint applied through SDL_SetTextureColorMod.
@OptIn(ExperimentalForeignApi::class)
internal class SdlImageBitmap(
	inRenderer: COpaquePointer,
	override val width: Int,
	override val height: Int,
	override val config: ImageBitmapConfig,
	override val hasAlpha: Boolean,
	override val colorSpace: ColorSpace,
) : ImageBitmap {

	// RGBA render-target texture, premultiplied blend for compositing back (content
	// is drawn over a transparent clear with ordinary BLEND, leaving premultiplied
	// colours — see Sdl3ClipTargets for the same reasoning).
	val texture: COpaquePointer? = SDL_CreateTexture(
		inRenderer.reinterpret(),
		SDL_PIXELFORMAT_RGBA32,
		SDL_TextureAccess.SDL_TEXTUREACCESS_TARGET,
		maxOf(1, width),
		maxOf(1, height),
	)?.also { SDL_SetTextureBlendMode(it.reinterpret(), SDL_BLENDMODE_BLEND_PREMULTIPLIED) }

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

// Registered on Sdl3RenderBackend; hands the vendored DrawCache a real offscreen.
@OptIn(ExperimentalForeignApi::class)
internal class Sdl3OffscreenRenderer(
	private val fRenderer: COpaquePointer,
	private val fTextRenderer: Sdl3TextRenderer?,
) : OffscreenRenderer {

	override fun createImageBitmap(
		width: Int,
		height: Int,
		config: ImageBitmapConfig,
		hasAlpha: Boolean,
		colorSpace: ColorSpace,
	): ImageBitmap = SdlImageBitmap(fRenderer, width, height, config, hasAlpha, colorSpace)

	override fun createCanvas(image: ImageBitmap): Canvas? {
		val vBmp = image as? SdlImageBitmap ?: return null
		val vTex = vBmp.texture ?: return null
		return Sdl3Canvas(
			fRenderer,
			Size(vBmp.width.toFloat(), vBmp.height.toFloat()),
			fTextRenderer,
			fOffscreenTexture = vTex,
			// A single-colour icon is rasterised as an Alpha8 mask: draw it WHITE so
			// the tint applied on blit (SDL_SetTextureColorMod, a multiply) yields the
			// tint colour where covered instead of multiplying a black icon to black.
			fForceWhite = vBmp.config == ImageBitmapConfig.Alpha8,
		)
	}
}
