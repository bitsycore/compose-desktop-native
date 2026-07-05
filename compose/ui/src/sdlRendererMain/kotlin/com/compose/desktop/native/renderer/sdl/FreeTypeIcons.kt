package com.compose.desktop.native.renderer.sdl

import androidx.compose.ui.text.font.FontVariation
import com.compose.desktop.native.icons.IconFont
import freetype.*
import kotlinx.cinterop.*
import sdl3.*
import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.desktop.native.graphics.r8
import com.compose.desktop.native.graphics.g8
import com.compose.desktop.native.graphics.b8
import com.compose.desktop.native.graphics.a8

// ==================
// MARK: FreeTypeIcons
// ==================

/* Variable-font icon rasterisation for the SDL3 renderer. SDL3_ttf 3.2's
   public API has no axis-set, so we go directly to FreeType for icon-font
   families — FT_Set_Var_Design_Coordinates drives the OpenType FILL /
   wght / GRAD / opsz axes per face.

   Lifetime:
   - One FT_Library for the whole renderer (init / destroy).
   - One "base" FT_Face per family (used only for axis-metadata queries).
   - One "variant" FT_Face per (family, normalised-variations-string),
     pre-set to the requested design coordinates. Each variant carries the
     last pixel size it was set to so consecutive same-size draws don't
     re-call FT_Set_Pixel_Sizes.
   - One SDL_Texture per (family, variations, codepoint, pixel size, tint
     colour), cached on first render and re-blitted thereafter.

   Each cached entry holds a copy of the font bytes on the native heap so
   FT_New_Memory_Face's memory stays valid for the face's lifetime
   (FT_Done_Face frees the face but does NOT free the buffer we provided —
   that's our responsibility). */
@OptIn(ExperimentalForeignApi::class)
internal class FreeTypeIcons {

	// ============
	//  Config

	/* Supersample factor: glyphs are rendered at this multiple of the
	   requested physical pixel size, then downsampled with a kSS×kSS box
	   filter on the way into the SDL surface. 2 = 4 samples per output
	   pixel — enough to merge the inner-ring gaps that variable-font
	   interpolation produces at extreme axis combinations, without making
	   first-render cost prohibitive. */
	private val kSupersampleFactor: Int = 2

	// ============
	//  State

	private var fLibrary: FT_Library? = null

	private class FaceBytes(val mem: CPointer<ByteVar>, val size: Int)

	private class FamilyState(
		val baseFace: CPointer<FT_FaceRec>,
		val bytes: FaceBytes,
		/* axis index keyed by 4-char OpenType tag (e.g. "wght" → 1). */
		val axisIndexByTag: Map<String, Int>,
		/* 16.16-fixed-point default coords for every axis, in font order. */
		val defaultCoords: LongArray,
	)

	private val fFamilies = mutableMapOf<String, FamilyState?>()

	private class Variant(
		val face: CPointer<FT_FaceRec>,
		var pixelSize: Int = -1,
	)

	private data class VariantKey(val family: String, val variationsKey: String)

	private val fVariants = mutableMapOf<VariantKey, Variant>()

	private data class GlyphKey(
		val family: String,
		val variationsKey: String,
		val codepoint: Int,
		val pixelSize: Int,
		val argb: Int,
	)

	private class CachedGlyph(
		val tex: COpaquePointer,
		val w: Int,
		val h: Int,
		// pen offsets in pixels — top-left of the bitmap relative to the
		// glyph's pen origin; FreeType returns these in face.glyph.bitmap_left
		// (always positive offset to the right) and bitmap_top (positive
		// offset upward from baseline).
		val bearingLeft: Int,
		val bearingTop: Int,
	)

	private val fGlyphs = mutableMapOf<GlyphKey, CachedGlyph?>()

	// ============
	//  Init / destroy

	fun init(): Boolean {
		if (fLibrary != null) return true
		memScoped {
			val vLibPtr = alloc<FT_LibraryVar>()
			val vErr = FT_Init_FreeType(vLibPtr.ptr)
			if (vErr.toInt() != 0) {
				println("FreeTypeIcons: FT_Init_FreeType failed ($vErr)")
				return false
			}
			fLibrary = vLibPtr.value
		}
		return true
	}

	fun destroy() {
		for (vGlyph in fGlyphs.values) {
			vGlyph?.tex?.let { SDL_DestroyTexture(it.reinterpret()) }
		}
		fGlyphs.clear()

		for (vVariant in fVariants.values) {
			FT_Done_Face(vVariant.face)
		}
		fVariants.clear()

		for (vFam in fFamilies.values) {
			if (vFam != null) {
				FT_Done_Face(vFam.baseFace)
				nativeHeap.free(vFam.bytes.mem)
			}
		}
		fFamilies.clear()

		fLibrary?.let { FT_Done_FreeType(it) }
		fLibrary = null
	}

	// ============
	//  Public API

	/* Paints a single-codepoint icon at the given pen position with the
	   requested tint. Returns false (without drawing) if FreeType isn't
	   initialised, the family isn't registered with IconFont, or the
	   codepoint has no glyph in the font. Callers that get false can fall
	   back to the SDL3_ttf path. */
	fun drawGlyph(
		inSdlRenderer: COpaquePointer,
		inFamily: String,
		inCodepoint: Int,
		inPixelSize: Int,
		inColor: ComposeColor,
		inVariations: List<FontVariation.Setting>,
		inBoxX: Int,
		inBoxY: Int,
		inBoxW: Int,
		inBoxH: Int,
		inDpr: Float,
	): Boolean {
		if (fLibrary == null && !init()) return false

		val vVariationsKey = variationsKey(inVariations)
		// Rasterise at PHYSICAL pixels so the bitmap matches the back-buffer
		// resolution (SDL_SetRenderScale stretches the dst rect by DPR). Keep
		// the cache key on logical size + DPR so a 2x context and 1x context
		// don't clobber each other.
		val vPhys = (inPixelSize * inDpr).toInt().coerceAtLeast(1)
		val vKey = GlyphKey(inFamily, vVariationsKey, inCodepoint, vPhys, inColor.toArgb())
		val vGlyph: CachedGlyph? = if (fGlyphs.containsKey(vKey)) {
			fGlyphs[vKey]
		} else {
			val vNew = rasterise(inSdlRenderer, inFamily, inCodepoint, vPhys, inColor, inVariations, vVariationsKey)
			fGlyphs[vKey] = vNew
			vNew
		}
		if (vGlyph == null) return false

		// Glyph bitmap is in physical pixels; the SDL renderer scale brings
		// it back to logical when we blit. Centre in the box, then SNAP the
		// top-left to a whole device pixel — a fractional device offset makes
		// SDL bilinear-sample the texture (the main cause of blurry / fringed
		// icons). vLogW * dpr == vGlyph.w exactly, so the size already maps 1:1.
		val vLogW = vGlyph.w / inDpr
		val vLogH = vGlyph.h / inDpr
		val vDstX = kotlin.math.round((inBoxX + (inBoxW - vLogW) / 2f) * inDpr) / inDpr
		val vDstY = kotlin.math.round((inBoxY + (inBoxH - vLogH) / 2f) * inDpr) / inDpr
		blit(inSdlRenderer, vGlyph, vDstX, vDstY, vLogW, vLogH)
		return true
	}

	fun hasFamily(inFamily: String): Boolean = IconFont.isIconFamily(inFamily)

	// ============
	//  Family / variant / glyph resolution

	private fun resolveFamily(inFamily: String): FamilyState? {
		fFamilies[inFamily]?.let { return it }
		if (fFamilies.containsKey(inFamily)) return null  // cached miss

		val vBytes = IconFont.bytesFor(inFamily)
		if (vBytes == null) {
			fFamilies[inFamily] = null
			return null
		}
		val vLib = fLibrary
		if (vLib == null) {
			fFamilies[inFamily] = null
			return null
		}

		// Copy bytes to native heap — FreeType requires the buffer to live
		// for the face's lifetime.
		val vMem = nativeHeap.allocArray<ByteVar>(vBytes.size)
		vBytes.usePinned { vPinned ->
			platform.posix.memcpy(vMem, vPinned.addressOf(0), vBytes.size.convert())
		}

		val vBaseFace: CPointer<FT_FaceRec> = memScoped {
			val vSlot = alloc<FT_FaceVar>()
			val vErr = FT_New_Memory_Face(vLib, vMem.reinterpret(), vBytes.size.convert(), 0, vSlot.ptr)
			if (vErr.toInt() != 0 || vSlot.value == null) {
				println("FreeTypeIcons: FT_New_Memory_Face failed for '$inFamily' ($vErr)")
				nativeHeap.free(vMem)
				fFamilies[inFamily] = null
				return null
			}
			vSlot.value!!
		}

		// Read variable-font axis metadata: tag → index map + defaults.
		val vAxisIndex = mutableMapOf<String, Int>()
		val vDefaults: LongArray = memScoped {
			val vMmPtr = alloc<CPointerVar<FT_MM_Var>>()
			val vErr = FT_Get_MM_Var(vBaseFace, vMmPtr.ptr)
			if (vErr.toInt() != 0 || vMmPtr.value == null) {
				println("FreeTypeIcons: '$inFamily' is not a variable font (FT_Get_MM_Var → $vErr)")
				return@memScoped LongArray(0)
			}
			val vMm = vMmPtr.value!!.pointed
			val vCount = vMm.num_axis.toInt()
			val vAxes = vMm.axis!!
			val vDefs = LongArray(vCount)
			for (i in 0 until vCount) {
				val vAxis = vAxes[i]
				val vTagInt = vAxis.tag.toLong() and 0xFFFFFFFFL
				val vTag = tagFromInt(vTagInt.toInt())
				vAxisIndex[vTag] = i
				vDefs[i] = vAxis.def.toLong()
			}
			FT_Done_MM_Var(vLib, vMmPtr.value)
			vDefs
		}

		val vState = FamilyState(vBaseFace, FaceBytes(vMem, vBytes.size), vAxisIndex, vDefaults)
		fFamilies[inFamily] = vState
		return vState
	}

	private fun getOrCreateVariant(
		inFamily: String,
		inFamilyState: FamilyState,
		inVariations: List<FontVariation.Setting>,
		inVariationsKey: String,
	): Variant? {
		val vKey = VariantKey(inFamily, inVariationsKey)
		fVariants[vKey]?.let { return it }

		val vLib = fLibrary ?: return null
		val vBytes = inFamilyState.bytes

		// Open a fresh face from the same bytes; design coords mutate the
		// face, so siblings can't share one.
		val vFace: CPointer<FT_FaceRec> = memScoped {
			val vSlot = alloc<FT_FaceVar>()
			val vErr = FT_New_Memory_Face(vLib, vBytes.mem.reinterpret(), vBytes.size.convert(), 0, vSlot.ptr)
			if (vErr.toInt() != 0 || vSlot.value == null) {
				println("FreeTypeIcons: variant FT_New_Memory_Face failed ($vErr)")
				return null
			}
			vSlot.value!!
		}

		// Build full coords array: start from defaults, overlay requested.
		if (inFamilyState.defaultCoords.isNotEmpty()) {
			memScoped {
				val vCount = inFamilyState.defaultCoords.size
				val vCoords = allocArray<FT_FixedVar>(vCount)
				for (i in 0 until vCount) vCoords[i] = inFamilyState.defaultCoords[i].convert()
				for (vVar in inVariations) {
					val vIdx = inFamilyState.axisIndexByTag[vVar.axisName] ?: continue
					vCoords[vIdx] = (vVar.toVariationValue(null).toDouble() * 65536.0).toLong().convert()
				}
				FT_Set_Var_Design_Coordinates(vFace, vCount.convert(), vCoords)
			}
		}

		val vVariant = Variant(vFace)
		fVariants[vKey] = vVariant
		return vVariant
	}

	private fun rasterise(
		inSdlRenderer: COpaquePointer,
		inFamily: String,
		inCodepoint: Int,
		inPixelSize: Int,
		inColor: ComposeColor,
		inVariations: List<FontVariation.Setting>,
		inVariationsKey: String,
	): CachedGlyph? {
		val vFamily = resolveFamily(inFamily) ?: return null
		val vVariant = getOrCreateVariant(inFamily, vFamily, inVariations, inVariationsKey) ?: return null

		// Only the FILL axis interpolation produces the overlapping / near-
		// touching contours that need supersampling + embolden to render
		// cleanly. For the common case (no fill) render at the EXACT target
		// size with FreeType's own anti-aliasing — far crisper at UI sizes
		// than a 2× render boxed back down. Filled icons keep the 2× path.
		val vFilled = inVariations.any { it.axisName == "FILL" && it.toVariationValue(null) > 0f }
		val vSS = if (vFilled) kSupersampleFactor else 1
		val vRenderPx = inPixelSize * vSS
		if (vVariant.pixelSize != vRenderPx) {
			FT_Set_Pixel_Sizes(vVariant.face, 0u, vRenderPx.convert())
			vVariant.pixelSize = vRenderPx
		}

		val vGlyphIdx = FT_Get_Char_Index(vVariant.face, inCodepoint.convert())
		if (vGlyphIdx.toInt() == 0) return null
		// Load outline (no render yet); we need to flip on FT_OUTLINE_OVERLAP
		// before rasterising. Material Symbols' FILL axis interpolates
		// between an outlined and a filled design — at intermediate axis
		// values (and combinations like fill=1 + wght=100) the resulting
		// outline has overlapping subpaths, which the rasterizer renders as
		// "holes" under the default nonzero winding unless we explicitly
		// tell it to handle overlaps.
		// Load without hinting — TT hinting quantises subpixel positions
		// onto integer pixel grids, which interacts badly with the
		// interpolated outlines variable fonts produce at non-master axis
		// combinations (the rounding leaves thin gaps between contours that
		// then show as visible "inner rings" after rasterisation).
		val vLoadFlags = FT_LOAD_DEFAULT.toInt() or FT_LOAD_NO_HINTING.toInt()
		val vErr = FT_Load_Glyph(vVariant.face, vGlyphIdx, vLoadFlags)
		if (vErr.toInt() != 0) return null

		val vSlotPtr = vVariant.face.pointed.glyph ?: return null
		val vSlot = vSlotPtr.pointed
		// OVERLAP + HIGH_PRECISION cover most cases; the supersample step
		// in uploadBitmap handles the pixel-level anti-aliasing. What's
		// left at extreme axis combinations (e.g. fill=1 + GRAD=200) is a
		// real geometric gap between the outer outline and the inner fill
		// contour — supersampling alone can't close it. We Embolden the
		// outline by a sub-pixel amount so the two contours overlap
		// instead of nearly-touching; the box filter then merges them
		// into one solid mass.
		vSlot.outline.flags = vSlot.outline.flags or
				FT_OUTLINE_OVERLAP.toInt() or
				FT_OUTLINE_HIGH_PRECISION.toInt()
		// Embolden ONLY the filled path, to close the geometric gap between
		// the outer outline and the inner fill contour. On plain outlined
		// icons it just fattens strokes and merges fine detail (gear teeth,
		// the trash-can bars), so skip it there. Strength is 26.6 fixed-point
		// font units; at the 2× fill render that is two source pixels.
		if (vFilled) {
			FT_Outline_Embolden(vSlot.outline.ptr, (2L * 64L).convert())
		}

		val vRenderErr = FT_Render_Glyph(vSlotPtr, FT_RENDER_MODE_NORMAL)
		if (vRenderErr.toInt() != 0) return null

		val vBitmap = vSlot.bitmap
		val vSrcW = vBitmap.width.toInt()
		val vSrcH = vBitmap.rows.toInt()
		if (vSrcW <= 0 || vSrcH <= 0) return null

		val vTex = uploadBitmap(inSdlRenderer, vBitmap, inColor, vSS) ?: return null
		// CachedGlyph dimensions are POST-downsample (texture size), so the
		// later blit math gives the right logical size.
		return CachedGlyph(
			tex = vTex,
			w = (vSrcW + vSS - 1) / vSS,
			h = (vSrcH + vSS - 1) / vSS,
			bearingLeft = vSlot.bitmap_left / vSS,
			bearingTop = vSlot.bitmap_top / vSS,
		)
	}

	// ============
	//  Bitmap → tinted ARGB texture

	/* FreeType's normal-mode bitmap is 8-bit grayscale (FT_PIXEL_MODE_GRAY,
	   coverage 0..255) at kSupersampleFactor× the target size. We
	   downsample with a kSupersampleFactor² box filter on the way into an
	   ARGB8888 SDL_Surface where RGB = tint colour, A = downsampled
	   coverage. Pitch can be negative (top-down) — we walk source rows by
	   sign. The box-filter step is what fuses the thin inner-ring gaps
	   produced by FreeType's interpolation of overlapping subpaths. */
	private fun uploadBitmap(
		inSdlRenderer: COpaquePointer,
		inBitmap: FT_Bitmap,
		inColor: ComposeColor,
		inSS: Int,
	): COpaquePointer? {
		val vSrcW = inBitmap.width.toInt()
		val vSrcH = inBitmap.rows.toInt()
		val vPitch = inBitmap.pitch
		val vBuf = inBitmap.buffer ?: return null
		val vSS = inSS

		// Output is the downsampled size — round up so a 49×49 source at 2×
		// gives a 25×25 dst (last row/col samples partial cells).
		val vDstW = (vSrcW + vSS - 1) / vSS
		val vDstH = (vSrcH + vSS - 1) / vSS

		val vSurface = SDL_CreateSurface(vDstW, vDstH, SDL_PIXELFORMAT_ARGB8888) ?: return null
		val vDst = vSurface.pointed.pixels?.reinterpret<UByteVar>() ?: run {
			SDL_DestroySurface(vSurface)
			return null
		}
		val vDstPitch = vSurface.pointed.pitch
		val vR = inColor.r8.toUByte()
		val vG = inColor.g8.toUByte()
		val vB = inColor.b8.toUByte()
		val vColorAlpha = inColor.a8

		val vNorm = vSS * vSS
		for (vDy in 0 until vDstH) {
			val vDstRowBase = vDy * vDstPitch
			val vSyStart = vDy * vSS
			for (vDx in 0 until vDstW) {
				val vSxStart = vDx * vSS
				// Sum coverage over the kSupersampleFactor×kSupersampleFactor
				// source cell; cells that fall off the edge contribute 0.
				var vSum = 0
				for (vSy in 0 until vSS) {
					val vY = vSyStart + vSy
					if (vY >= vSrcH) break
					val vSrcRow = if (vPitch >= 0) vY * vPitch else (vSrcH - 1 - vY) * (-vPitch)
					for (vSx in 0 until vSS) {
						val vX = vSxStart + vSx
						if (vX >= vSrcW) break
						vSum += vBuf[vSrcRow + vX].toInt() and 0xFF
					}
				}
				val vCov = vSum / vNorm
				val vA = ((vCov * vColorAlpha) / 255).coerceIn(0, 255)
				val vBase = vDstRowBase + vDx * 4
				// ARGB8888 in little-endian memory is laid out [B, G, R, A].
				vDst[vBase + 0] = vB
				vDst[vBase + 1] = vG
				vDst[vBase + 2] = vR
				vDst[vBase + 3] = vA.toUByte()
			}
		}

		val vTexture = SDL_CreateTextureFromSurface(inSdlRenderer.reinterpret(), vSurface)
		SDL_DestroySurface(vSurface)
		return vTexture
	}

	private fun blit(
		inSdlRenderer: COpaquePointer,
		inGlyph: CachedGlyph,
		inDstX: Float,
		inDstY: Float,
		inDstW: Float,
		inDstH: Float,
	) {
		memScoped {
			val vDst = alloc<SDL_FRect>()
			vDst.x = inDstX
			vDst.y = inDstY
			vDst.w = inDstW
			vDst.h = inDstH
			SDL_RenderTexture(inSdlRenderer.reinterpret(), inGlyph.tex.reinterpret(), null, vDst.ptr)
		}
	}

	// ============
	//  Helpers

	private fun variationsKey(inV: List<FontVariation.Setting>): String {
		if (inV.isEmpty()) return ""
		return inV.sortedBy { it.axisName }.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }
	}

	/* Unpacks a packed 32-bit OpenType tag into its 4-char ASCII string. */
	private fun tagFromInt(inTag: Int): String {
		val vChars = CharArray(4)
		vChars[0] = ((inTag shr 24) and 0xFF).toChar()
		vChars[1] = ((inTag shr 16) and 0xFF).toChar()
		vChars[2] = ((inTag shr 8) and 0xFF).toChar()
		vChars[3] = (inTag and 0xFF).toChar()
		return vChars.concatToString()
	}

	private fun ComposeColor.toArgb(): Int = (a8 shl 24) or (r8 shl 16) or (g8 shl 8) or b8
}
