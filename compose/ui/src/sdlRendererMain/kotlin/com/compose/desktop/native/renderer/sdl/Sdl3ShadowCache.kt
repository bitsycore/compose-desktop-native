package com.compose.desktop.native.renderer.sdl

import kotlinx.cinterop.*
import sdl3.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

// ==================
// MARK: Sdl3ShadowCache
// ==================

/* Cached drop-shadow tiles for the SDL renderer.

   A shadow's falloff only depends on (corner radius, blur radius) — never on
   the shape's overall size — so one small canonical tile per (radius, blur)
   pair serves every shadow via SDL_RenderTexture9Grid: the four corner
   quadrants stay fixed, the 1px-wide centre cross stretches. Compared to the
   tessellated ring stack this turns a shadow from up to 12 rounded-rect fills
   PER FRAME into a single 9-slice blit of a ≤ ~100×100 texture.

   The tile is computed on the CPU once per key: per-pixel signed distance to
   the canonical rounded rect, pushed through a Gaussian-CDF-shaped falloff
   (0.5·(1 − tanh(k·d/blur)) — tanh ≈ erf), which is smoother than any ring
   count. Pixels are WHITE with the falloff in alpha, so the blit applies the
   tint via texture colour/alpha mod and colour never enters the cache key. */
internal class Sdl3ShadowCache(private val fRenderer: COpaquePointer) {

	internal class Entry(
		val tex: COpaquePointer,   // white ARGB tile, falloff in alpha (0..255 = 0..1)
		val corner: Int,           // non-stretchable corner size (radius + blur) in px
		val tile: Int,             // full tile edge = corner * 2 + 1
	)

	private data class Key(val radius: Int, val blur: Int)

	private val fCache = LruCache<Key, Entry>(64) { vEntry ->
		SDL_DestroyTexture(vEntry.tex.reinterpret())
	}

	fun get(inRadiusPx: Int, inBlurPx: Int): Entry? {
		val vKey = Key(inRadiusPx, inBlurPx)
		fCache[vKey]?.let { return it }
		val vEntry = build(inRadiusPx, inBlurPx) ?: return null
		fCache[vKey] = vEntry
		return vEntry
	}

	fun destroy() {
		fCache.clear()
	}

	/* Rasterises the canonical tile: a rounded rect of corner radius `inRadius`
	   whose edge sits `inBlur` px inside the tile border, alpha = falloff of
	   the signed distance to that edge. */
	private fun build(inRadius: Int, inBlur: Int): Entry? {
		val vCorner = inRadius + inBlur
		val vTile = vCorner * 2 + 1
		val vSurface = SDL_CreateSurface(vTile, vTile, SDL_PIXELFORMAT_ARGB8888) ?: return null
		val vPixels = vSurface.pointed.pixels?.reinterpret<UByteVar>() ?: run {
			SDL_DestroySurface(vSurface)
			return null
		}
		val vPitch = vSurface.pointed.pitch

		// Shape half-extent: edge inset by blur from the tile border; the
		// centre pixel is the stretchable 9-grid cross.
		val vHalf = inRadius + 0.5f
		val vCentre = vCorner + 0.5f       // tile centre in pixel coords
		val vR = inRadius.toFloat()
		val vBlurF = max(1f, inBlur.toFloat())
		val vSteep = 1.8f                  // tanh(1.8·d/blur): ~erf of a σ≈blur/2 Gaussian

		for (vY in 0 until vTile) {
			val vRow = vY * vPitch
			for (vX in 0 until vTile) {
				// Signed distance from the pixel centre to the rounded rect
				// (standard rounded-box SDF, evaluated in |quadrant| space).
				val vPx = kotlin.math.abs(vX + 0.5f - vCentre) - (vHalf - vR)
				val vPy = kotlin.math.abs(vY + 0.5f - vCentre) - (vHalf - vR)
				val vQx = max(vPx, 0f)
				val vQy = max(vPy, 0f)
				val vDist = sqrt(vQx * vQx + vQy * vQy) + min(max(vPx, vPy), 0f) - vR
				// Gaussian-CDF-shaped falloff: 1 deep inside, 0.5 at the edge,
				// → 0 at edge + blur.
				val vAlpha = 0.5f * (1f - tanh(vSteep * vDist / vBlurF))
				val vA = (vAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
				val vBase = vRow + vX * 4
				// ARGB8888 little-endian memory layout: [B, G, R, A].
				vPixels[vBase + 0] = 255u
				vPixels[vBase + 1] = 255u
				vPixels[vBase + 2] = 255u
				vPixels[vBase + 3] = vA.toUByte()
			}
		}

		val vTexture = SDL_CreateTextureFromSurface(fRenderer.reinterpret(), vSurface)
		SDL_DestroySurface(vSurface)
		if (vTexture == null) return null
		return Entry(vTexture, vCorner, vTile)
	}
}
