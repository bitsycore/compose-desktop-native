package com.compose.desktop.native.renderer.sdl

import com.compose.desktop.native.*

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.WrappedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.*
import kotlin.math.pow
import kotlin.math.roundToInt
import sdl3.SDL_ConvertSurface
import sdl3.SDL_CreateTextureFromSurface
import sdl3.SDL_DestroyTexture
import sdl3.SDL_FRect
import sdl3.SDL_GetError
import sdl3.SDL_GetTextureSize
import sdl3.SDL_PIXELFORMAT_ARGB8888
import sdl3.SDL_RenderTexture
import sdl3.SDL_DestroySurface
import sdl3.SDL_Color
import sdl3_ttf.TTF_CloseFont
import sdl3_ttf.TTF_GetFontHeight
import sdl3_ttf.TTF_GetStringSize
import sdl3_ttf.TTF_Init
import sdl3_ttf.TTF_OpenFont
import sdl3_ttf.TTF_Quit
import sdl3_ttf.TTF_RenderText_Blended

// ==================
// MARK: Sdl3TextRenderer (mingwX64 fallback)
// ==================

/* Text via SDL3_ttf. Caches TTF_Font per size, and a per-(text, color,
   fontSize) SDL_Texture so repeated frames don't re-rasterise the same
   string. Implements TextMeasurer so the common layout pass agrees
   with what we paint. */
internal class Sdl3TextRenderer(private val backend: SDL3Backend) {

    // HiDPI scale. Fonts are opened at fontSize * DPR pixels so the
    // rasterised glyph texture matches the physical pixel count of the
    // logical-size dst rect after SDL_SetRenderScale stretches it.
    // Measurement results are divided back by DPR so layout still works
    // in logical points.
    private var fDpr: Float = 1f

    fun setDpr(inDpr: Float) {
        if (inDpr == fDpr) return
        fDpr = inDpr
        // Invalidate everything keyed off the old DPR-baked sizes.
        for (v in fTextureCache.values) SDL_DestroyTexture(v.tex.reinterpret())
        fTextureCache.clear()
        for (f in fFontCache.values) TTF_CloseFont(f.reinterpret())
        fFontCache.clear()
        fWidthCache.clear()
    }

    // Cached TTF_Font handles keyed by logical pixel size.
    private val fFontCache = mutableMapOf<Int, COpaquePointer>()

    // Cached glyph textures: (text, fontSize, packedARGB) → texture + size.
    private data class TextureKey(val text: String, val fontSize: Int, val color: Int)
    private data class CachedTexture(val tex: COpaquePointer, val w: Int, val h: Int)
    private val fTextureCache = mutableMapOf<TextureKey, CachedTexture>()

    // Cached measured widths in LOGICAL points keyed by (text, fontSize).
    private val fWidthCache = mutableMapOf<Pair<String, Int>, Int>()

    /* Returns false if TTF couldn't init. */
    fun init(): Boolean {
        if (!TTF_Init()) {
            println("TTF_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }
        return true
    }

    fun destroy() {
        for (v in fTextureCache.values) SDL_DestroyTexture(v.tex.reinterpret())
        fTextureCache.clear()
        for (f in fFontCache.values) TTF_CloseFont(f.reinterpret())
        fFontCache.clear()
        TTF_Quit()
    }

    val textMeasurer: TextMeasurer = object : TextMeasurer {
        override fun measure(inText: String, inFontSize: Int, inMaxWidth: Int): IntSize {
            val vWrap = wrap(inText, inFontSize, inMaxWidth)
            val vWidth = if (vWrap.lines.isEmpty()) 0
                         else vWrap.lines.maxOf { measureWidth(it, inFontSize) }
            val vLine = lineHeight(inFontSize).toInt().coerceAtLeast(1)
            return IntSize(vWidth, vLine * vWrap.lines.size.coerceAtLeast(1))
        }

        override fun wrap(inText: String, inFontSize: Int, inMaxWidth: Int): WrappedText =
            wrapTextWithStarts(inText, inFontSize, inMaxWidth)

        override fun lineHeight(inFontSize: Int): Float {
            val vFont = getFont(inFontSize) ?: return inFontSize * 1.3f
            // TTF_GetFontHeight returns physical pixels (we opened the
            // font at fontSize * DPR). Convert back to logical.
            return (TTF_GetFontHeight(vFont.reinterpret()).toFloat() / fDpr).coerceAtLeast(1f)
        }
    }

    /* Greedy soft-wrap that mirrors the Skia renderer's algorithm so the
       cross-platform behaviour stays identical. Hard lines on '\n'; long
       lines split at whitespace, ultra-long words split mid-word. */
    private fun wrapTextWithStarts(inText: String, inFontSize: Int, inMaxWidth: Int): WrappedText {
        if (inText.isEmpty()) return WrappedText(listOf(""), intArrayOf(0))
        val vLines = mutableListOf<String>()
        val vStarts = mutableListOf<Int>()
        val vUnbounded = inMaxWidth >= Int.MAX_VALUE / 2

        var vHardStart = 0
        while (vHardStart <= inText.length) {
            val vNl = inText.indexOf('\n', vHardStart)
            val vHardEnd = if (vNl < 0) inText.length else vNl
            val vHard = inText.substring(vHardStart, vHardEnd)
            if (vUnbounded || vHard.isEmpty() || measureWidth(vHard, inFontSize) <= inMaxWidth) {
                vLines.add(vHard); vStarts.add(vHardStart)
            } else {
                wrapHardLine(vHard, inFontSize, inMaxWidth, vHardStart, vLines, vStarts)
            }
            if (vNl < 0) break
            vHardStart = vNl + 1
        }
        return WrappedText(vLines, vStarts.toIntArray())
    }

    private fun wrapHardLine(
        inLine: String,
        inFontSize: Int,
        inMaxWidth: Int,
        inBaseOffset: Int,
        outLines: MutableList<String>,
        outStarts: MutableList<Int>,
    ) {
        var vCurrent = StringBuilder()
        var vLineStartInHard = 0
        var i = 0
        while (i < inLine.length) {
            val vWordStart = i
            while (i < inLine.length && !inLine[i].isWhitespace()) i++
            while (i < inLine.length && inLine[i].isWhitespace()) i++
            val vWord = inLine.substring(vWordStart, i)
            val vCandidate = vCurrent.toString() + vWord
            if (measureWidth(vCandidate, inFontSize) <= inMaxWidth) {
                vCurrent.append(vWord)
            } else {
                if (vCurrent.isNotEmpty()) {
                    outLines.add(vCurrent.toString())
                    outStarts.add(inBaseOffset + vLineStartInHard)
                    vLineStartInHard += vCurrent.length
                    vCurrent = StringBuilder()
                }
                if (measureWidth(vWord, inFontSize) > inMaxWidth) {
                    val vSub = StringBuilder()
                    for (ch in vWord) {
                        if (measureWidth(vSub.toString() + ch, inFontSize) > inMaxWidth) {
                            if (vSub.isNotEmpty()) {
                                outLines.add(vSub.toString())
                                outStarts.add(inBaseOffset + vLineStartInHard)
                                vLineStartInHard += vSub.length
                                vSub.clear()
                            }
                        }
                        vSub.append(ch)
                    }
                    vCurrent.append(vSub)
                } else {
                    vCurrent.append(vWord)
                }
            }
        }
        if (vCurrent.isNotEmpty()) {
            outLines.add(vCurrent.toString())
            outStarts.add(inBaseOffset + vLineStartInHard)
        }
    }

    /* Returns LOGICAL-point width. The font was opened at fontSize*DPR
       so TTF_GetStringSize reports physical pixels — divide by DPR to
       get back to logical. */
    private fun measureWidth(inText: String, inFontSize: Int): Int {
        if (inText.isEmpty()) return 0
        fWidthCache[inText to inFontSize]?.let { return it }
        val vFont = getFont(inFontSize) ?: run {
            val vEst = (inText.length * inFontSize * 0.6f).toInt()
            fWidthCache[inText to inFontSize] = vEst
            return vEst
        }
        var vPhys = 0
        memScoped {
            val vW = alloc<IntVar>()
            val vH = alloc<IntVar>()
            // Length = 0 → SDL_ttf calls strlen on the UTF-8 string. Don't
            // pass inText.length: that's UTF-16 code-unit count, but the C
            // side wants byte count. Non-ASCII chars (e.g. em-dash → 3
            // UTF-8 bytes) would otherwise truncate the tail of the string.
            if (TTF_GetStringSize(vFont.reinterpret(), inText, 0u, vW.ptr, vH.ptr)) {
                vPhys = vW.value
            }
        }
        val vLogical = (vPhys / fDpr).toInt()
        fWidthCache[inText to inFontSize] = vLogical
        return vLogical
    }

    /* Renders one already-wrapped line at (inX, inY) inside a box of
       (inBoxWidth, inBoxHeight). Caches the rasterised texture so calling
       this every frame for the same string is cheap. */
    fun drawText(
        inText: String,
        inX: Int,
        inY: Int,
        inBoxWidth: Int,
        inBoxHeight: Int,
        inColor: ComposeColor,
        inFontSize: Int,
        inAlign: TextAlign,
    ) {
        if (inText.isEmpty()) return
        val vRenderer = backend.renderer ?: return
        val vCached = getOrCreateTexture(inText, inFontSize, inColor) ?: return

        // Texture dimensions are physical pixels (rasterised at fontSize *
        // DPR). Convert to logical for the dst rect so SDL_SetRenderScale's
        // stretch brings it back to the same pixel size — i.e. 1:1, crisp.
        val vLogW = vCached.w / fDpr
        val vLogH = vCached.h / fDpr

        val vPenX = when (inAlign) {
            TextAlign.Start  -> inX.toFloat()
            TextAlign.Center -> inX + (inBoxWidth - vLogW) / 2f
            TextAlign.End    -> inX + (inBoxWidth - vLogW)
        }
        // Vertically centre — matches the Skia path which cap-centres the
        // glyphs inside the box.
        val vPenY = inY + (inBoxHeight - vLogH) / 2f

        // Snap the blit origin to the physical pixel grid. The glyph texture is
        // rasterised once at physical size, so drawing it at a fractional
        // physical position resamples (softens) it. Vertically-centred labels
        // — e.g. the sidebar items in a 40dp box — land on a half-pixel when
        // (boxHeight - textHeight) is odd, which is the main source of blur.
        // round(v * dpr) / dpr keeps the blit 1:1, matching Skia's crispness.
        fun snap(inV: Float): Float = kotlin.math.round(inV * fDpr) / fDpr

        memScoped {
            val vDst = alloc<SDL_FRect>()
            vDst.x = snap(vPenX)
            vDst.y = snap(vPenY)
            vDst.w = vLogW
            vDst.h = vLogH
            SDL_RenderTexture(vRenderer.reinterpret(), vCached.tex.reinterpret(), null, vDst.ptr)
        }
    }

    // Coverage gamma: alpha' = 255*(alpha/255)^kTextGamma. kTextGamma < 1
    // boosts partial coverage so antialiased stems read heavier and smoother
    // for light text on a dark background — closer to Skia's gamma-corrected
    // glyphs. Tune toward 1.0 to lighten, toward 0.6 to thicken further.
    private val kTextGamma = 0.72f
    private val fGammaLut = UByteArray(256) { i ->
        (255f * (i / 255f).pow(kTextGamma)).roundToInt().coerceIn(0, 255).toUByte()
    }

    /* Gamma-boosts the alpha (coverage) channel of an ARGB8888 glyph surface
       in place. Runs once per cached string. */
    private fun applyCoverageGamma(inSurface: CPointer<sdl3.SDL_Surface>) {
        val vS = inSurface.pointed
        val vPixels = vS.pixels?.reinterpret<UByteVar>() ?: return
        val vW = vS.w
        val vH = vS.h
        val vPitch = vS.pitch
        // ARGB8888 is 0xAARRGGBB; little-endian → alpha is byte 3 of each pixel.
        for (y in 0 until vH) {
            val vRow = y * vPitch
            for (x in 0 until vW) {
                val vAi = vRow + x * 4 + 3
                vPixels[vAi] = fGammaLut[vPixels[vAi].toInt()]
            }
        }
    }

    private fun getOrCreateTexture(
        inText: String,
        inFontSize: Int,
        inColor: ComposeColor,
    ): CachedTexture? {
        val vKey = TextureKey(inText, inFontSize, inColor.toArgb())
        fTextureCache[vKey]?.let { return it }

        val vRenderer = backend.renderer ?: return null
        val vFont = getFont(inFontSize) ?: return null
        val vTex = memScoped {
            val vColor = alloc<SDL_Color>()
            vColor.r = inColor.r8.toUByte()
            vColor.g = inColor.g8.toUByte()
            vColor.b = inColor.b8.toUByte()
            vColor.a = inColor.a8.toUByte()
            val vSurface = TTF_RenderText_Blended(
                vFont.reinterpret(),
                inText,
                // 0 → strlen on the UTF-8 string (see measureWidth).
                0u,
                vColor.readValue(),
            ) ?: return@memScoped null
            // sdl3 and sdl3_ttf cinterops both declare SDL_Surface — they
            // refer to the same C struct but Kotlin sees them as distinct
            // types. Reinterpret to bridge, then convert to a known ARGB
            // layout so we can gamma-boost the coverage before uploading.
            val vBlended = vSurface.reinterpret<sdl3.SDL_Surface>()
            val vArgb = SDL_ConvertSurface(vBlended, SDL_PIXELFORMAT_ARGB8888)
            SDL_DestroySurface(vBlended)
            if (vArgb == null) return@memScoped null
            applyCoverageGamma(vArgb)
            val vTexture = SDL_CreateTextureFromSurface(vRenderer.reinterpret(), vArgb)
            SDL_DestroySurface(vArgb)
            vTexture
        } ?: return null

        val vSize = memScoped {
            val vW = alloc<FloatVar>()
            val vH = alloc<FloatVar>()
            SDL_GetTextureSize(vTex.reinterpret(), vW.ptr, vH.ptr)
            vW.value.toInt() to vH.value.toInt()
        }
        val vCached = CachedTexture(vTex, vSize.first, vSize.second)
        fTextureCache[vKey] = vCached
        return vCached
    }

    /* Opens the font at PHYSICAL pixels (logical fontSize × DPR) so the
       rasterised glyphs match the back buffer's resolution. The cache key
       is the logical size — setDpr clears the cache when DPR changes. */
    private fun getFont(inSize: Int): COpaquePointer? {
        fFontCache[inSize]?.let { return it }

        val vPhysicalSize = (inSize * fDpr).coerceAtLeast(1f)
        // Load the bundled font from composeResources/font first; fall back to
        // common system fonts so text still renders without the default font
        // (e.g. when built with -PbundleDefaultFont=false).
        val vPaths = listOfNotNull(
            composeResourceFullPath("font/Roboto-Regular.ttf"),
            "C:\\Windows\\Fonts\\segoeui.ttf",
            "C:\\Windows\\Fonts\\arial.ttf",
        )
        for (path in vPaths) {
            val vFont = TTF_OpenFont(path, vPhysicalSize)
            if (vFont != null) {
                fFontCache[inSize] = vFont
                return vFont
            }
        }
        println("Sdl3TextRenderer: no usable font found for size $inSize")
        return null
    }

    private fun ComposeColor.toArgb(): Int =
        (a8 shl 24) or (r8 shl 16) or (g8 shl 8) or b8
}
