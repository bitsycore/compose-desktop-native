package com.compose.desktop.native.renderer.sdl

import androidx.compose.ui.text.font.FontVariation
import freetype.*
import kotlinx.cinterop.*
import sdl3.*
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.desktop.native.graphics.r8
import com.compose.desktop.native.graphics.g8
import com.compose.desktop.native.graphics.b8
import com.compose.desktop.native.graphics.a8

// Coverage gamma: alpha' = 255*(cov/255)^kTextGamma. With kTextGamma < 1 this
// boosts partial coverage so antialiased stems read heavier and smoother for
// light text on a dark background — closer to Skia's gamma-corrected coverage.
// Mirrors Sdl3TextRenderer's kTextGamma so FreeType-rasterised variable-weight
// text matches the SDL3_ttf path instead of looking thin.
private const val kTextGamma = 0.72f
private val fTextGammaLut = UByteArray(256) { i ->
	(255f * (i / 255f).pow(kTextGamma)).roundToInt().coerceIn(0, 255).toUByte()
}

// ==================
// MARK: FreeTypeText
// ==================

/* Renders runs of plain text through FreeType so the bundled variable
   Roboto's wght (and wdth) axis interpolation actually applies — SDL3_ttf
   3.2 has no variation-set API, so weighted text on the SDL3 path stays
   the regular weight without this. Mirrors FreeTypeIcons's lifetime
   model:
   - One FT_Library, init/destroy with the renderer.
   - One "base" face per family for axis metadata.
   - One "variant" face per (family, variations key), pre-set to design
     coords; carries the last requested pixel size so repeated draws at
     the same size skip FT_Set_Pixel_Sizes.
   - One SDL_Texture per (family, variations, text, pixel size, ARGB)
     so re-painting an unchanged label is just an SDL_RenderTexture.

   Unlike FreeTypeIcons (which renders single icon glyphs centred in a
   box, with supersampling for variable-axis artefacts), this class lays
   out a sequence of glyphs along a horizontal baseline using each
   glyph's horizontal advance — no kerning, no shaping, just left-to-
   right placement. That's enough for the Latin-script default Roboto.
   Right-to-left and complex shaping would need a real shaping layer
   (HarfBuzz) which we don't bundle. */
@OptIn(ExperimentalForeignApi::class)
internal class FreeTypeText {

	// ============
	//  State

	private var fLibrary: FT_Library? = null

	private class FaceBytes(val mem: CPointer<ByteVar>, val size: Int)

	private class FamilyState(
		val baseFace: CPointer<FT_FaceRec>,
		val bytes: FaceBytes,
		val axisIndexByTag: Map<String, Int>,
		val defaultCoords: LongArray,
	)

	private val fFamilies = mutableMapOf<String, FamilyState?>()

	private class Variant(val face: CPointer<FT_FaceRec>, var pixelSize: Int = -1)

	private data class VariantKey(val family: String, val variationsKey: String)
	private val fVariants = mutableMapOf<VariantKey, Variant>()

	private data class TextKey(
		val family: String,
		val variationsKey: String,
		val text: String,
		val pixelSize: Int,
		val argb: Int,
	)

	private class CachedText(val tex: COpaquePointer, val w: Int, val h: Int, val baseline: Int)
	private val fTextCache = mutableMapOf<TextKey, CachedText>()

	// Map of family-name → byte source. The default family ("" key) is
	// populated externally by the renderer at startup.
	private val fFontBytes = mutableMapOf<String, ByteArray>()

	// ============
	//  Lifecycle

	fun init(): Boolean {
		if (fLibrary != null) return true
		val vLib: FT_Library = memScoped {
			val vSlot = alloc<FT_LibraryVar>()
			val vErr = FT_Init_FreeType(vSlot.ptr)
			if (vErr.toInt() != 0 || vSlot.value == null) {
				println("FreeTypeText: FT_Init_FreeType failed ($vErr)")
				return false
			}
			vSlot.value!!
		}
		fLibrary = vLib
		return true
	}

	fun destroy() {
		for (vT in fTextCache.values) SDL_DestroyTexture(vT.tex.reinterpret())
		fTextCache.clear()
		for (vV in fVariants.values) FT_Done_Face(vV.face)
		fVariants.clear()
		for (vF in fFamilies.values) vF?.let {
			FT_Done_Face(it.baseFace)
			nativeHeap.free(it.bytes.mem)
		}
		fFamilies.clear()
		fLibrary?.let { FT_Done_FreeType(it) }
		fLibrary = null
	}

	/* Register a font's bytes under a family key. Family "" is the
	   default font (Roboto in this codebase); other keys would let
	   callers register additional named families later. */
	fun registerFontBytes(inFamily: String, inBytes: ByteArray) {
		fFontBytes[inFamily] = inBytes
	}

	// ============
	//  Capability check

	/* True if this family is registered and can be rendered through
	   FreeType. The caller (Sdl3TextRenderer) uses this to decide
	   whether to route a text run through here vs through SDL3_ttf. */
	fun hasFamily(inFamily: String?): Boolean = (inFamily ?: "") in fFontBytes

	// ============
	//  Public draw entry point

	/* Lay out and blit `inText` at (inX, inY) (logical points). Returns
	   true on success; false to fall back to the SDL3_ttf path. */
	fun drawString(
		inSdlRenderer: COpaquePointer,
		inFamily: String?,
		inText: String,
		inPixelSize: Int,
		inColor: ComposeColor,
		inVariations: List<FontVariation.Setting>,
		inX: Float,
		inY: Float,
		inDpr: Float,
	): Boolean {
		if (fLibrary == null && !init()) return false
		if (inText.isEmpty()) return true
		val vFamilyKey = inFamily ?: ""
		val vBytes = fFontBytes[vFamilyKey] ?: return false
		val vState = resolveFamily(vFamilyKey, vBytes) ?: return false
		val vVarsKey = variationsKey(inVariations)
		val vVariant = getOrCreateVariant(vFamilyKey, vState, inVariations, vVarsKey) ?: return false

		val vPhys = (inPixelSize * inDpr).toInt().coerceAtLeast(1)
		val vKey = TextKey(vFamilyKey, vVarsKey, inText, vPhys, inColor.toArgb())
		val vEntry = fTextCache[vKey] ?: run {
			val vNew = rasteriseString(inSdlRenderer, vVariant, inText, vPhys, inColor) ?: return false
			fTextCache[vKey] = vNew
			vNew
		}

		// Cached texture is in physical pixels; the renderer scale will
		// re-stretch to logical when we hand it a logical-coord dst rect.
		val vLogW = vEntry.w / inDpr
		val vLogH = vEntry.h / inDpr
		memScoped {
			val vDst = alloc<SDL_FRect>()
			vDst.x = inX
			vDst.y = inY
			vDst.w = vLogW
			vDst.h = vLogH
			SDL_RenderTexture(inSdlRenderer.reinterpret(), vEntry.tex.reinterpret(), null, vDst.ptr)
		}
		return true
	}

	/* Return the logical-point width of `inText` at the requested size +
	   variations. Used by the TextMeasurer path so layout matches what
	   draw will produce. */
	fun measureString(
		inFamily: String?,
		inText: String,
		inPixelSize: Int,
		inVariations: List<FontVariation.Setting>,
	): Int {
		if (fLibrary == null && !init()) return 0
		if (inText.isEmpty()) return 0
		val vFamilyKey = inFamily ?: ""
		val vBytes = fFontBytes[vFamilyKey] ?: return 0
		val vState = resolveFamily(vFamilyKey, vBytes) ?: return 0
		val vVariant = getOrCreateVariant(vFamilyKey, vState, inVariations, variationsKey(inVariations)) ?: return 0
		setPixelSize(vVariant, inPixelSize)

		var vAdvance26d6 = 0L
		for (vC in inText) {
			val vGlyphIdx = FT_Get_Char_Index(vVariant.face, vC.code.convert())
			if (vGlyphIdx.toInt() == 0) continue
			val vErr = FT_Load_Glyph(vVariant.face, vGlyphIdx, FT_LOAD_DEFAULT.toInt())
			if (vErr.toInt() != 0) continue
			val vSlot = vVariant.face.pointed.glyph?.pointed ?: continue
			vAdvance26d6 += vSlot.advance.x
		}
		// 26.6 fixed → integer pixels.
		return ((vAdvance26d6 + 32L) ushr 6).toInt()
	}

	/* Line height in logical points: ascender + descender from the
	   face metrics at the requested size. */
	fun lineHeight(
		inFamily: String?,
		inPixelSize: Int,
		inVariations: List<FontVariation.Setting>,
	): Float {
		if (fLibrary == null && !init()) return inPixelSize * 1.3f
		val vFamilyKey = inFamily ?: ""
		val vBytes = fFontBytes[vFamilyKey] ?: return inPixelSize * 1.3f
		val vState = resolveFamily(vFamilyKey, vBytes) ?: return inPixelSize * 1.3f
		val vVariant = getOrCreateVariant(vFamilyKey, vState, inVariations, variationsKey(inVariations)) ?: return inPixelSize * 1.3f
		setPixelSize(vVariant, inPixelSize)
		val vSize = vVariant.face.pointed.size?.pointed ?: return inPixelSize * 1.3f
		// 26.6 fixed-point in pixel units after FT_Set_Pixel_Sizes.
		val vH = (vSize.metrics.height ushr 6).toInt()
		return vH.toFloat().coerceAtLeast(1f)
	}

	// ============
	//  Family resolution (parallels FreeTypeIcons; bytes come from
	//  registerFontBytes rather than IconFont.bytesFor).

	private fun resolveFamily(inFamily: String, inBytes: ByteArray): FamilyState? {
		fFamilies[inFamily]?.let { return it }
		if (fFamilies.containsKey(inFamily)) return null

		val vLib = fLibrary ?: return null
		val vMem = nativeHeap.allocArray<ByteVar>(inBytes.size)
		inBytes.usePinned { vPinned ->
			platform.posix.memcpy(vMem, vPinned.addressOf(0), inBytes.size.convert())
		}
		val vBaseFace: CPointer<FT_FaceRec> = memScoped {
			val vSlot = alloc<FT_FaceVar>()
			val vErr = FT_New_Memory_Face(vLib, vMem.reinterpret(), inBytes.size.convert(), 0, vSlot.ptr)
			if (vErr.toInt() != 0 || vSlot.value == null) {
				println("FreeTypeText: FT_New_Memory_Face failed for '$inFamily' ($vErr)")
				nativeHeap.free(vMem)
				fFamilies[inFamily] = null
				return null
			}
			vSlot.value!!
		}
		val vAxisIndex = mutableMapOf<String, Int>()
		val vDefaults: LongArray = memScoped {
			val vMmPtr = alloc<CPointerVar<FT_MM_Var>>()
			val vErr = FT_Get_MM_Var(vBaseFace, vMmPtr.ptr)
			if (vErr.toInt() != 0 || vMmPtr.value == null) return@memScoped LongArray(0)
			val vMm = vMmPtr.value!!.pointed
			val vCount = vMm.num_axis.toInt()
			val vAxes = vMm.axis!!
			val vDefs = LongArray(vCount)
			for (vI in 0 until vCount) {
				val vAxis = vAxes[vI]
				val vTagInt = (vAxis.tag.toLong() and 0xFFFFFFFFL).toInt()
				vAxisIndex[tagFromInt(vTagInt)] = vI
				vDefs[vI] = vAxis.def.toLong()
			}
			FT_Done_MM_Var(vLib, vMmPtr.value)
			vDefs
		}
		val vState = FamilyState(vBaseFace, FaceBytes(vMem, inBytes.size), vAxisIndex, vDefaults)
		fFamilies[inFamily] = vState
		return vState
	}

	private fun getOrCreateVariant(
		inFamily: String,
		inState: FamilyState,
		inVariations: List<FontVariation.Setting>,
		inVariationsKey: String,
	): Variant? {
		val vKey = VariantKey(inFamily, inVariationsKey)
		fVariants[vKey]?.let { return it }

		val vLib = fLibrary ?: return null
		val vBytes = inState.bytes
		val vFace: CPointer<FT_FaceRec> = memScoped {
			val vSlot = alloc<FT_FaceVar>()
			val vErr = FT_New_Memory_Face(vLib, vBytes.mem.reinterpret(), vBytes.size.convert(), 0, vSlot.ptr)
			if (vErr.toInt() != 0 || vSlot.value == null) return null
			vSlot.value!!
		}
		// Apply requested variations on top of font defaults.
		if (inState.defaultCoords.isNotEmpty()) {
			memScoped {
				val vCoords = allocArray<FT_FixedVar>(inState.defaultCoords.size)
				for (vI in inState.defaultCoords.indices) vCoords[vI] = inState.defaultCoords[vI].convert()
				for (vVar in inVariations) {
					val vIdx = inState.axisIndexByTag[vVar.axisName] ?: continue
					// Convert float → 16.16 fixed-point font units.
					vCoords[vIdx] = (vVar.toVariationValue(null) * 65536f).toLong().convert()
				}
				FT_Set_Var_Design_Coordinates(vFace, inState.defaultCoords.size.convert(), vCoords)
			}
		}
		val vVariant = Variant(vFace)
		fVariants[vKey] = vVariant
		return vVariant
	}

	private fun setPixelSize(inVariant: Variant, inPixelSize: Int) {
		if (inVariant.pixelSize == inPixelSize) return
		FT_Set_Pixel_Sizes(inVariant.face, 0u, inPixelSize.convert())
		inVariant.pixelSize = inPixelSize
	}

	// ============
	//  String rasterisation: walk the codepoints, paint each into a
	//  shared ARGB surface positioned by the running pen, return as
	//  one SDL_Texture.

	private fun rasteriseString(
		inSdlRenderer: COpaquePointer,
		inVariant: Variant,
		inText: String,
		inPixelSize: Int,
		inColor: ComposeColor,
	): CachedText? {
		setPixelSize(inVariant, inPixelSize)
		val vSize = inVariant.face.pointed.size?.pointed ?: return null
		val vAscent = (vSize.metrics.ascender ushr 6).toInt()
		val vDescent = ((-vSize.metrics.descender) ushr 6).toInt()
		val vLineHeight = vAscent + vDescent

		// First pass: measure total width so we know how big the surface
		// needs to be.
		var vTotalAdvance26d6 = 0L
		for (vC in inText) {
			val vGlyphIdx = FT_Get_Char_Index(inVariant.face, vC.code.convert())
			if (vGlyphIdx.toInt() == 0) continue
			if (FT_Load_Glyph(inVariant.face, vGlyphIdx, FT_LOAD_DEFAULT.toInt()).toInt() != 0) continue
			val vSlot = inVariant.face.pointed.glyph?.pointed ?: continue
			vTotalAdvance26d6 += vSlot.advance.x
		}
		val vTextW = ((vTotalAdvance26d6 + 32L) ushr 6).toInt().coerceAtLeast(1)
		val vTextH = vLineHeight.coerceAtLeast(1)

		val vSurface = SDL_CreateSurface(vTextW, vTextH, SDL_PIXELFORMAT_ARGB8888) ?: return null
		val vPixels = vSurface.pointed.pixels?.reinterpret<UByteVar>() ?: run {
			SDL_DestroySurface(vSurface)
			return null
		}
		val vDstPitch = vSurface.pointed.pitch
		// Clear the surface to transparent black (ARGB8888 LE = BGRA in
		// memory, so zeroing is fine for both colour and alpha).
		platform.posix.memset(vPixels, 0, (vDstPitch * vTextH).convert())

		val vR = inColor.r8.toUByte()
		val vG = inColor.g8.toUByte()
		val vB = inColor.b8.toUByte()
		val vColorAlpha = inColor.a8

		var vPenX26d6 = 0L
		for (vC in inText) {
			val vGlyphIdx = FT_Get_Char_Index(inVariant.face, vC.code.convert())
			if (vGlyphIdx.toInt() == 0) {
				vPenX26d6 += (inPixelSize.toLong() shl 6) / 4L
				continue
			}
			val vLoadFlags = FT_LOAD_DEFAULT.toInt() or FT_LOAD_NO_HINTING.toInt()
			if (FT_Load_Glyph(inVariant.face, vGlyphIdx, vLoadFlags).toInt() != 0) continue
			val vSlotPtr = inVariant.face.pointed.glyph ?: continue
			val vSlot = vSlotPtr.pointed
			if (FT_Render_Glyph(vSlotPtr, FT_RENDER_MODE_NORMAL).toInt() != 0) continue
			val vBitmap = vSlot.bitmap
			val vSrcW = vBitmap.width.toInt()
			val vSrcH = vBitmap.rows.toInt()
			val vPenX = (vPenX26d6 ushr 6).toInt()
			val vXOffset = vPenX + vSlot.bitmap_left
			val vYOffset = vAscent - vSlot.bitmap_top

			if (vSrcW > 0 && vSrcH > 0) {
				val vBuf = vBitmap.buffer
				val vPitch = vBitmap.pitch
				if (vBuf != null) {
					for (vRow in 0 until vSrcH) {
						val vDy = vYOffset + vRow
						if (vDy < 0 || vDy >= vTextH) continue
						val vSrcRow = if (vPitch >= 0) vRow * vPitch else (vSrcH - 1 - vRow) * (-vPitch)
						for (vCol in 0 until vSrcW) {
							val vDx = vXOffset + vCol
							if (vDx < 0 || vDx >= vTextW) continue
							val vCov = vBuf[vSrcRow + vCol].toInt() and 0xFF
							if (vCov == 0) continue
							val vA = ((fTextGammaLut[vCov].toInt() * vColorAlpha) / 255).coerceIn(0, 255)
							val vBase = vDy * vDstPitch + vDx * 4
							// Premultiply NOT needed; we use straight ARGB
							// with ALPHA blending at render time.
							val vExistingA = vPixels[vBase + 3].toInt() and 0xFF
							if (vA > vExistingA) {
								vPixels[vBase + 0] = vB
								vPixels[vBase + 1] = vG
								vPixels[vBase + 2] = vR
								vPixels[vBase + 3] = vA.toUByte()
							}
						}
					}
				}
			}
			vPenX26d6 += vSlot.advance.x
		}

		val vTex = SDL_CreateTextureFromSurface(inSdlRenderer.reinterpret(), vSurface)
		SDL_DestroySurface(vSurface)
		if (vTex == null) return null
		return CachedText(vTex, vTextW, vTextH, vAscent)
	}

	// ============
	//  Helpers

	private fun variationsKey(inV: List<FontVariation.Setting>): String {
		if (inV.isEmpty()) return ""
		return inV.sortedBy { it.axisName }.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }
	}

	private fun tagFromInt(inTag: Int): String {
		val vA = ((inTag ushr 24) and 0xFF).toChar()
		val vB = ((inTag ushr 16) and 0xFF).toChar()
		val vC = ((inTag ushr 8) and 0xFF).toChar()
		val vD = (inTag and 0xFF).toChar()
		return "$vA$vB$vC$vD"
	}

	private fun ComposeColor.toArgb(): Int = (a8 shl 24) or (r8 shl 16) or (g8 shl 8) or b8
}
