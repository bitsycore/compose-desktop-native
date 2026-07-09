package com.compose.sdl.renderer.sdl

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import com.compose.sdl.graphics.OffscreenRenderer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.usePinned
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
	// A pre-made texture (the encoded-image decode path — see
	// Sdl3EncodedImageDecoder); null → create a render TARGET for the
	// vector-rasterisation path below.
	inDecodedTexture: COpaquePointer? = null,
) : ImageBitmap {

	// RGBA render-target texture, premultiplied blend for compositing back (content
	// is drawn over a transparent clear with ordinary BLEND, leaving premultiplied
	// colours — see Sdl3ClipTargets for the same reasoning).
	val texture: COpaquePointer? = inDecodedTexture ?: SDL_CreateTexture(
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

// ==================
// MARK: Sdl3EncodedImageDecoder — encoded bytes → drawable ImageBitmap
// ==================

/* Registered on the com.compose.sdl.graphics decode hook; backs
   :components-resources' painterResource / SVG path on the SDL renderer.
   IMG_Load_IO auto-detects the container (png/jpg/bmp/gif/webp/svg — SVG is
   rasterised at its intrinsic size). Decoded surfaces carry STRAIGHT alpha,
   so the texture gets the ordinary BLEND mode (unlike the premultiplied
   render-target path above). */
@OptIn(ExperimentalForeignApi::class)
internal class Sdl3EncodedImageDecoder(
	private val fRenderer: COpaquePointer,
) : com.compose.sdl.graphics.EncodedImageDecoder {

	override fun decode(inBytes: ByteArray): ImageBitmap? {
		if (inBytes.isEmpty()) return null
		val vSurface = inBytes.usePinned { vPinned ->
			val vIo = SDL_IOFromConstMem(vPinned.addressOf(0), inBytes.size.convert())
				?: return@usePinned null
			sdl3_image.IMG_Load_IO(vIo.reinterpret(), true)
		} ?: return null
		val vSdlSurface = vSurface.reinterpret<SDL_Surface>()
		val vW = vSdlSurface.pointed.w
		val vH = vSdlSurface.pointed.h
		val vTex = SDL_CreateTextureFromSurface(fRenderer.reinterpret(), vSdlSurface)
		SDL_DestroySurface(vSdlSurface)
		if (vTex == null) return null
		SDL_SetTextureBlendMode(vTex.reinterpret(), SDL_BLENDMODE_BLEND)
		return SdlImageBitmap(
			fRenderer, vW, vH,
			config = ImageBitmapConfig.Argb8888,
			hasAlpha = true,
			colorSpace = androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb,
			inDecodedTexture = vTex,
		)
	}
}
