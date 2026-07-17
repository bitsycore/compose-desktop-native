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
// Mutable native handles held BY REFERENCE so a Cleaner can free them without
// capturing the (collectable) SdlImageBitmap. Both the explicit close() and the
// GC Cleaner enqueue release of whatever is still here — the first to run nulls
// the fields, so the other is a no-op (they can't overlap: close() only runs
// while the bitmap is reachable, the Cleaner only after it isn't).
@OptIn(ExperimentalForeignApi::class)
private class SdlBitmapHandles(
	var texture: COpaquePointer?,
	var surface: CPointer<SDL_Surface>?,
)

/* Enqueue destruction of the handles' current contents on the main thread
   (SDL calls aren't thread-safe; the Cleaner runs on a GC worker). Idempotent:
   clears the fields so a second call frees nothing. Top-level so the Cleaner
   block never captures the bitmap. */
@OptIn(ExperimentalForeignApi::class)
private fun enqueueRelease(inHandles: SdlBitmapHandles) {
	val vTex = inHandles.texture
	val vSurf = inHandles.surface
	if (vTex == null && vSurf == null) return
	inHandles.texture = null
	inHandles.surface = null
	com.compose.sdl.graphics.NativeReleaseQueue.enqueue {
		if (vSurf != null) SDL_DestroySurface(vSurf)
		if (vTex != null) SDL_DestroyTexture(vTex.reinterpret())
	}
}

@OptIn(ExperimentalForeignApi::class)
internal class SdlImageBitmap(
	private val fRenderer: COpaquePointer,
	override val width: Int,
	override val height: Int,
	override val config: ImageBitmapConfig,
	override val hasAlpha: Boolean,
	override val colorSpace: ColorSpace,
	// A surface decoded OFF the main thread (the encoded-image path — see
	// Sdl3EncodedImageDecoder); converted to a texture on the first draw.
	// null → create a render TARGET for the vector-rasterisation path below
	// (that path always constructs on the main thread).
	fDecodedSurface: CPointer<SDL_Surface>? = null,
) : ImageBitmap {

	// RGBA render-target texture, premultiplied blend for compositing back (content
	// is drawn over a transparent clear with ordinary BLEND, leaving premultiplied
	// colours — see Sdl3ClipTargets for the same reasoning).
	private val fHandles = SdlBitmapHandles(
		texture = if (fDecodedSurface != null) null else SDL_CreateTexture(
			fRenderer.reinterpret(),
			SDL_PIXELFORMAT_RGBA32,
			SDL_TextureAccess.SDL_TEXTUREACCESS_TARGET,
			maxOf(1, width),
			maxOf(1, height),
		)?.also { SDL_SetTextureBlendMode(it.reinterpret(), SDL_BLENDMODE_BLEND_PREMULTIPLIED) },
		surface = fDecodedSurface,
	)

	// GC backstop: if nothing calls close(), free the native handles when the
	// bitmap is collected (deferred to the main thread via the queue). Ownership
	// via close() is the primary path; this catches leaks.
	@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
	private val fCleaner = kotlin.native.ref.createCleaner(fHandles) { enqueueRelease(it) }

	// SDL renderer calls are NOT thread-safe, and the resources pipeline
	// decodes on Dispatchers.Default workers — so the decode path hands over a
	// plain SDL_Surface and the texture is created here, lazily, on the first
	// access (createCanvas / the draw paths — main thread only). Decoded
	// surfaces carry STRAIGHT alpha → ordinary BLEND, unlike the premultiplied
	// render-target path above.
	val texture: COpaquePointer?
		get() {
			fHandles.surface?.let { vSurface ->
				fHandles.texture = SDL_CreateTextureFromSurface(fRenderer.reinterpret(), vSurface)
					?.also { SDL_SetTextureBlendMode(it.reinterpret(), SDL_BLENDMODE_BLEND) }
				SDL_DestroySurface(vSurface)
				fHandles.surface = null
			}
			return fHandles.texture
		}

	/* Free the texture/surface now (deferred to the main thread). Call on
	   cache eviction / removeMemoryResource so native memory releases promptly
	   instead of waiting for a GC. The Cleaner remains as a backstop. */
	fun close() = enqueueRelease(fHandles)

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

	// Phase 4: a capture canvas records shape geometry into [list] and touches NO GPU
	// (text/image/clip mark the list unsupported). Used by SdlDisplayListRenderNode to
	// record a leaf's layer-local geometry. No render target — no timing nondeterminism.
	internal fun createCaptureCanvas(list: SdlDisplayList, size: Size): Sdl3Canvas =
		Sdl3Canvas(fRenderer, size, fTextRenderer, fCaptureList = list)
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
		// Pure decode only — IMG_Load_IO touches no renderer state, so it is
		// safe on the resources pipeline's Dispatchers.Default workers. The
		// texture is created from this surface on the first MAIN-THREAD draw
		// (SdlImageBitmap.texture) — SDL renderer calls are not thread-safe.
		val vSurface = inBytes.usePinned { vPinned ->
			val vIo = SDL_IOFromConstMem(vPinned.addressOf(0), inBytes.size.convert())
				?: return@usePinned null
			sdl3_image.IMG_Load_IO(vIo.reinterpret(), true)
		} ?: return null
		val vSdlSurface = vSurface.reinterpret<SDL_Surface>()
		return SdlImageBitmap(
			fRenderer, vSdlSurface.pointed.w, vSdlSurface.pointed.h,
			config = ImageBitmapConfig.Argb8888,
			hasAlpha = true,
			colorSpace = androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb,
			fDecodedSurface = vSdlSurface,
		)
	}
}
