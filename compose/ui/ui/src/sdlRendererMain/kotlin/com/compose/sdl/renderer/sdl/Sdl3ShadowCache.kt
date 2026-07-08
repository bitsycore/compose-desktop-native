package com.compose.sdl.renderer.sdl

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
		fGenericCache.clear()
	}

	// ============
	//  Generic-path shadows — CutCornerShape, GenericShape, … resolve to a
	//  path Outline, which doesn't 9-slice (arbitrary geometry doesn't
	//  stretch). Instead: flatten the path, rasterise a filled mask at shape
	//  size + blur padding, box-blur the alpha 3× (≈ Gaussian), upload once.
	//  Keyed by (path-geometry hash, blur); the mask is white-with-alpha so
	//  tint/alpha still ride the blit's colour/alpha mod.

	internal class GenericEntry(
		val tex: COpaquePointer,
		val w: Int,               // texture size in px
		val h: Int,
		val originX: Float,       // texture (0,0) in path space = bounds.min - padding
		val originY: Float,
	)

	private data class GenericKey(val geometry: Long, val blur: Int)

	private val fGenericCache = LruCache<GenericKey, GenericEntry>(32) { vEntry ->
		SDL_DestroyTexture(vEntry.tex.reinterpret())
	}

	fun getGeneric(inPath: androidx.compose.ui.graphics.Path, inBlurPx: Int): GenericEntry? {
		val vProject = inPath as? com.compose.sdl.graphics.ProjectPath ?: return null
		val vKey = GenericKey(geometryHash(vProject), inBlurPx)
		fGenericCache[vKey]?.let { return it }
		val vEntry = buildGeneric(vProject, inBlurPx) ?: return null
		fGenericCache[vKey] = vEntry
		return vEntry
	}

	/* Order-sensitive fold of every command coordinate — two paths with the
	   same geometry hash the same regardless of the Path INSTANCE (outlines
	   are re-created per layer update, so identity can't key the cache). */
	private fun geometryHash(inPath: com.compose.sdl.graphics.ProjectPath): Long {
		var vHash = 1125899906842597L
		fun fold(inV: Float) { vHash = vHash * 31 + inV.toRawBits() }
		for (vCmd in inPath.commands) when (vCmd) {
			is com.compose.sdl.graphics.PathCommand.MoveTo -> { fold(1f); fold(vCmd.x); fold(vCmd.y) }
			is com.compose.sdl.graphics.PathCommand.LineTo -> { fold(2f); fold(vCmd.x); fold(vCmd.y) }
			is com.compose.sdl.graphics.PathCommand.QuadTo -> { fold(3f); fold(vCmd.cx); fold(vCmd.cy); fold(vCmd.x); fold(vCmd.y) }
			is com.compose.sdl.graphics.PathCommand.CubicTo -> { fold(4f); fold(vCmd.c1x); fold(vCmd.c1y); fold(vCmd.c2x); fold(vCmd.c2y); fold(vCmd.x); fold(vCmd.y) }
			com.compose.sdl.graphics.PathCommand.Close -> fold(5f)
		}
		return vHash
	}

	private fun buildGeneric(inPath: com.compose.sdl.graphics.ProjectPath, inBlur: Int): GenericEntry? {
		// Flatten to polyline contours (same curve sampling as the tessellator).
		val vContours = flattenPath(inPath)
		if (vContours.isEmpty()) return null
		var vMinX = Float.MAX_VALUE; var vMinY = Float.MAX_VALUE
		var vMaxX = -Float.MAX_VALUE; var vMaxY = -Float.MAX_VALUE
		for (vC in vContours) {
			var vI = 0
			while (vI < vC.size) {
				val vX = vC[vI]; val vY = vC[vI + 1]
				if (vX < vMinX) vMinX = vX; if (vX > vMaxX) vMaxX = vX
				if (vY < vMinY) vMinY = vY; if (vY > vMaxY) vMaxY = vY
				vI += 2
			}
		}
		if (vMaxX <= vMinX || vMaxY <= vMinY) return null
		val vPad = inBlur + 1
		val vW = (vMaxX - vMinX + 2 * vPad).toInt() + 1
		val vH = (vMaxY - vMinY + 2 * vPad).toInt() + 1
		if (vW <= 0 || vH <= 0 || vW > 2048 || vH > 2048) return null

		// Nonzero-winding scanline fill into an alpha mask.
		val vMask = ByteArray(vW * vH)
		for (vRow in 0 until vH) {
			val vSampleY = vRow + 0.5f - vPad + vMinY
			// Collect signed crossings of this scanline.
			val vXs = ArrayList<Pair<Float, Int>>(8)
			for (vC in vContours) {
				var vI = 0
				val vN = vC.size
				while (vI < vN) {
					val vX0 = vC[vI]; val vY0 = vC[vI + 1]
					val vJ = (vI + 2) % vN
					val vX1 = vC[vJ]; val vY1 = vC[vJ + 1]
					if ((vY0 <= vSampleY && vY1 > vSampleY) || (vY1 <= vSampleY && vY0 > vSampleY)) {
						val vT = (vSampleY - vY0) / (vY1 - vY0)
						vXs.add((vX0 + vT * (vX1 - vX0)) to (if (vY1 > vY0) 1 else -1))
					}
					vI += 2
				}
			}
			if (vXs.isEmpty()) continue
			vXs.sortBy { it.first }
			var vWinding = 0
			var vSpanStart = 0f
			for ((vX, vDir) in vXs) {
				val vWas = vWinding
				vWinding += vDir
				if (vWas == 0 && vWinding != 0) {
					vSpanStart = vX
				} else if (vWas != 0 && vWinding == 0) {
					val vFrom = (vSpanStart - vMinX + vPad).toInt().coerceIn(0, vW - 1)
					val vTo = (vX - vMinX + vPad).toInt().coerceIn(0, vW - 1)
					for (vCol in vFrom..vTo) vMask[vRow * vW + vCol] = -1  // 0xFF
				}
			}
		}

		// 3 separable box blurs ≈ Gaussian with σ ≈ blur/2; the blur also
		// anti-aliases the hard scanline edges.
		val vBoxR = (inBlur / 2).coerceAtLeast(1)
		repeat(3) {
			boxBlurH(vMask, vW, vH, vBoxR)
			boxBlurV(vMask, vW, vH, vBoxR)
		}

		// Upload as white ARGB with the mask in alpha.
		val vSurface = SDL_CreateSurface(vW, vH, SDL_PIXELFORMAT_ARGB8888) ?: return null
		val vPixels = vSurface.pointed.pixels?.reinterpret<UByteVar>() ?: run {
			SDL_DestroySurface(vSurface)
			return null
		}
		val vPitch = vSurface.pointed.pitch
		for (vRow in 0 until vH) {
			val vDstRow = vRow * vPitch
			val vSrcRow = vRow * vW
			for (vCol in 0 until vW) {
				val vBase = vDstRow + vCol * 4
				vPixels[vBase + 0] = 255u
				vPixels[vBase + 1] = 255u
				vPixels[vBase + 2] = 255u
				vPixels[vBase + 3] = vMask[vSrcRow + vCol].toUByte()
			}
		}
		val vTexture = SDL_CreateTextureFromSurface(fRenderer.reinterpret(), vSurface)
		SDL_DestroySurface(vSurface)
		if (vTexture == null) return null
		return GenericEntry(vTexture, vW, vH, vMinX - vPad, vMinY - vPad)
	}

	/* Flattens the command list into closed polyline contours (x,y pairs). */
	private fun flattenPath(inPath: com.compose.sdl.graphics.ProjectPath): List<FloatArray> {
		val vContours = ArrayList<FloatArray>()
		var vCurrent = ArrayList<Float>()
		var vCx = 0f; var vCy = 0f
		fun closeCurrent() {
			if (vCurrent.size >= 6) vContours.add(vCurrent.toFloatArray())
			vCurrent = ArrayList()
		}
		for (vCmd in inPath.commands) when (vCmd) {
			is com.compose.sdl.graphics.PathCommand.MoveTo -> {
				closeCurrent()
				vCx = vCmd.x; vCy = vCmd.y
				vCurrent.add(vCx); vCurrent.add(vCy)
			}
			is com.compose.sdl.graphics.PathCommand.LineTo -> {
				vCx = vCmd.x; vCy = vCmd.y
				vCurrent.add(vCx); vCurrent.add(vCy)
			}
			is com.compose.sdl.graphics.PathCommand.QuadTo -> {
				for (vI in 1..12) {
					val vT = vI / 12f; val vOne = 1f - vT
					vCurrent.add(vOne * vOne * vCx + 2f * vOne * vT * vCmd.cx + vT * vT * vCmd.x)
					vCurrent.add(vOne * vOne * vCy + 2f * vOne * vT * vCmd.cy + vT * vT * vCmd.y)
				}
				vCx = vCmd.x; vCy = vCmd.y
			}
			is com.compose.sdl.graphics.PathCommand.CubicTo -> {
				for (vI in 1..16) {
					val vT = vI / 16f; val vOne = 1f - vT
					vCurrent.add(
						vOne * vOne * vOne * vCx + 3f * vOne * vOne * vT * vCmd.c1x +
							3f * vOne * vT * vT * vCmd.c2x + vT * vT * vT * vCmd.x,
					)
					vCurrent.add(
						vOne * vOne * vOne * vCy + 3f * vOne * vOne * vT * vCmd.c1y +
							3f * vOne * vT * vT * vCmd.c2y + vT * vT * vT * vCmd.y,
					)
				}
				vCx = vCmd.x; vCy = vCmd.y
			}
			com.compose.sdl.graphics.PathCommand.Close -> closeCurrent()
		}
		closeCurrent()
		return vContours
	}

	/* In-place horizontal box blur on the alpha mask (sliding window sum). */
	private fun boxBlurH(inMask: ByteArray, inW: Int, inH: Int, inR: Int) {
		val vLine = IntArray(inW)
		val vNorm = 2 * inR + 1
		for (vRow in 0 until inH) {
			val vBase = vRow * inW
			var vSum = 0
			for (vI in -inR..inR) vSum += inMask[vBase + vI.coerceIn(0, inW - 1)].toInt() and 0xFF
			for (vCol in 0 until inW) {
				vLine[vCol] = vSum / vNorm
				val vAdd = (vCol + inR + 1).coerceAtMost(inW - 1)
				val vSub = (vCol - inR).coerceAtLeast(0)
				vSum += (inMask[vBase + vAdd].toInt() and 0xFF) - (inMask[vBase + vSub].toInt() and 0xFF)
			}
			for (vCol in 0 until inW) inMask[vBase + vCol] = vLine[vCol].toByte()
		}
	}

	/* In-place vertical box blur on the alpha mask. */
	private fun boxBlurV(inMask: ByteArray, inW: Int, inH: Int, inR: Int) {
		val vLine = IntArray(inH)
		val vNorm = 2 * inR + 1
		for (vCol in 0 until inW) {
			var vSum = 0
			for (vI in -inR..inR) vSum += inMask[vI.coerceIn(0, inH - 1) * inW + vCol].toInt() and 0xFF
			for (vRow in 0 until inH) {
				vLine[vRow] = vSum / vNorm
				val vAdd = (vRow + inR + 1).coerceAtMost(inH - 1)
				val vSub = (vRow - inR).coerceAtLeast(0)
				vSum += (inMask[vAdd * inW + vCol].toInt() and 0xFF) - (inMask[vSub * inW + vCol].toInt() and 0xFF)
			}
			for (vRow in 0 until inH) inMask[vRow * inW + vCol] = vLine[vRow].toByte()
		}
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
