package com.compose.sdl.renderer.sdl

import com.compose.sdl.*

import androidx.compose.ui.layout.ContentScale
import com.compose.sdl.res.AndroidVectorToSvg
import com.compose.sdl.res.ResourceKind
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.*
import sdl3.*
import sdl3_image.IMG_Load_IO
import sdl3_image.IMG_LoadSVG_IO
import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: Sdl3ImageCache
// ==================

/* Decodes bundled image resources to SDL_Textures via SDL3_image and caches
   them by relative path. Backs both the layout pass (intrinsicSize) and the
   renderer's draw — a resource is decoded exactly once.

   Raster (png/jpg/…) and .svg are loaded straight from the bundled file path
   with IMG_Load (auto-detecting the format). Android <vector> XML is read,
   converted to an SVG string, and rasterised from memory with IMG_LoadSVG_IO.
   Raw resources aren't drawable (use Res.readBytes). */
internal class Sdl3ImageCache(private val backend: SDL3Backend) {

	private class Cached(val tex: COpaquePointer, val w: Int, val h: Int)

	// Value is null when a decode failed — cached so we don't retry every frame.
	private val fCache = HashMap<String, Cached?>()

	fun intrinsicSize(inPath: String, inKind: ResourceKind): androidx.compose.ui.geometry.Size {
		val vCached = get(inPath, inKind) ?: return androidx.compose.ui.geometry.Size.Unspecified
		return androidx.compose.ui.geometry.Size(vCached.w.toFloat(), vCached.h.toFloat())
	}

	private fun get(inPath: String, inKind: ResourceKind): Cached? {
		if (fCache.containsKey(inPath)) return fCache[inPath]
		val vResult = decodeToTexture(inPath, inKind)
		fCache[inPath] = vResult
		if (vResult == null) println("Sdl3ImageCache: failed to decode $inPath: ${SDL_GetError()?.toKString()}")
		return vResult
	}

	// ==================
	// MARK: Decode
	// ==================

	private fun decodeToTexture(inPath: String, inKind: ResourceKind): Cached? {
		val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return null

		val vSurface = when (inKind) {
			ResourceKind.Raster, ResourceKind.Svg -> {
				val vBytes = loadComposeResourceBytes(inPath) ?: return null
				loadEncodedSurface(vBytes)
			}
			ResourceKind.AndroidVector -> {
				val vXml = loadComposeResourceBytes(inPath)?.decodeToString() ?: return null
				loadSvgSurface(AndroidVectorToSvg.convert(vXml))
			}
			ResourceKind.Raw -> return null
		} ?: return null

		// sdl3 and sdl3_image both declare SDL_Surface — same C struct, distinct
		// Kotlin types — so bridge with reinterpret (as Sdl3TextRenderer does).
		val vSdlSurface = vSurface.reinterpret<sdl3.SDL_Surface>()
		val vW = vSdlSurface.pointed.w
		val vH = vSdlSurface.pointed.h
		val vTex = SDL_CreateTextureFromSurface(vRenderer, vSdlSurface)
		SDL_DestroySurface(vSdlSurface)
		if (vTex == null) return null
		return Cached(vTex, vW, vH)
	}

	/* Decodes any IMG_-supported raster or SVG container from raw bytes
	   (png/jpg/bmp/gif/webp/svg). IMG_Load_IO peeks the bytes to auto-detect
	   the format; closeio = true so SDL closes its IOStream wrapper. The
	   underlying mem only needs to outlive the call (the surface owns its
	   pixels). */
	private fun loadEncodedSurface(inBytes: ByteArray): CPointer<sdl3.SDL_Surface>? {
		if (inBytes.isEmpty()) return null
		return inBytes.usePinned { vPinned ->
			val vIo = SDL_IOFromConstMem(vPinned.addressOf(0), inBytes.size.convert())
				?: return@usePinned null
			IMG_Load_IO(vIo.reinterpret(), true)
		}
	}

	/* Rasterises an SVG string from memory. */
	private fun loadSvgSurface(inSvg: String): CPointer<sdl3.SDL_Surface>? {
		val vBytes = inSvg.encodeToByteArray()
		if (vBytes.isEmpty()) return null
		return vBytes.usePinned { vPinned ->
			val vIo = SDL_IOFromConstMem(vPinned.addressOf(0), vBytes.size.convert()) ?: return@usePinned null
			val vSurf = IMG_LoadSVG_IO(vIo.reinterpret())
			SDL_CloseIO(vIo)
			vSurf
		}
	}

	// ==================
	// MARK: Draw
	// ==================

	/* Paints the resource into (inX, inY, inW, inH) logical points applying
	   contentScale + alpha. No-op if the resource isn't decodable. */
	fun draw(
		inPath: String,
		inKind: ResourceKind,
		inX: Float,
		inY: Float,
		inW: Float,
		inH: Float,
		inScale: ContentScale,
		inAlpha: Float,
	) {
		if (inW <= 0f || inH <= 0f) return
		val vCached = get(inPath, inKind) ?: return
		val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
		val vIw = vCached.w.toFloat()
		val vIh = vCached.h.toFloat()
		if (vIw <= 0f || vIh <= 0f) return

		// SDL_Texture is a public struct in SDL3; let .reinterpret() infer its
		// type from each SDL call's parameter (as Sdl3TextRenderer does).
		SDL_SetTextureAlphaMod(vCached.tex.reinterpret(), (inAlpha * 255f).toInt().coerceIn(0, 255).toUByte())

		memScoped {
			val vDst = alloc<SDL_FRect>()
			when (inScale) {
				ContentScale.FillBounds -> {
					vDst.x = inX; vDst.y = inY; vDst.w = inW; vDst.h = inH
					SDL_RenderTexture(vRenderer, vCached.tex.reinterpret(), null, vDst.ptr)
				}
				ContentScale.Crop -> {
					// Scale so the image fills the box, then show only the
					// centred sub-rectangle of the source that maps to it.
					val vScale = max(inW / vIw, inH / vIh)
					val vSrcW = inW / vScale
					val vSrcH = inH / vScale
					val vSrc = alloc<SDL_FRect>()
					vSrc.x = (vIw - vSrcW) / 2f
					vSrc.y = (vIh - vSrcH) / 2f
					vSrc.w = vSrcW
					vSrc.h = vSrcH
					vDst.x = inX; vDst.y = inY; vDst.w = inW; vDst.h = inH
					SDL_RenderTexture(vRenderer, vCached.tex.reinterpret(), vSrc.ptr, vDst.ptr)
				}
				else -> {
					// Fit / Inside / None: uniform scale, centred, full source.
					val vScale = when (inScale) {
						ContentScale.Fit    -> min(inW / vIw, inH / vIh)
						ContentScale.Inside -> min(1f, min(inW / vIw, inH / vIh))
						else                -> 1f   // None
					}
					val vDw = vIw * vScale
					val vDh = vIh * vScale
					vDst.x = inX + (inW - vDw) / 2f
					vDst.y = inY + (inH - vDh) / 2f
					vDst.w = vDw
					vDst.h = vDh
					SDL_RenderTexture(vRenderer, vCached.tex.reinterpret(), null, vDst.ptr)
				}
			}
		}
	}

	fun destroy() {
		for (vCached in fCache.values) {
			if (vCached != null) SDL_DestroyTexture(vCached.tex.reinterpret())
		}
		fCache.clear()
	}
}
