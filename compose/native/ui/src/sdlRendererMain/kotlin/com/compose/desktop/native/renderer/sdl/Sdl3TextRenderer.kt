package com.compose.desktop.native.renderer.sdl

import com.compose.desktop.native.*
import com.compose.desktop.native.icons.IconFont

import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.desktop.native.graphics.r8
import com.compose.desktop.native.graphics.g8
import com.compose.desktop.native.graphics.b8
import com.compose.desktop.native.graphics.a8
import com.compose.desktop.native.text.TextMeasurer
import com.compose.desktop.native.text.TextRendererCapabilities
import com.compose.desktop.native.text.WrappedText
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import com.compose.desktop.native.text.lineColorRuns
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
import sdl3_ttf.TTF_OpenFontIO
import sdl3_ttf.TTF_Quit
import sdl3_ttf.TTF_RenderText_Blended
import sdl3.SDL_IOFromConstMem

// ==================
// MARK: Sdl3TextRenderer (mingwX64 fallback)
// ==================

/* Text via SDL3_ttf. Caches TTF_Font per size, and a per-(text, color,
   fontSize) SDL_Texture so repeated frames don't re-rasterise the same
   string. Implements TextMeasurer so the common layout pass agrees
   with what we paint. */
internal class Sdl3TextRenderer(private val backend: SDL3Backend) {

    init {
        // SDL3_ttf 3.2 has no variable-axis API. We route icon-font draws
        // (anything registered in IconFont) through FreeType directly, which
        // honours FT_Set_Var_Design_Coordinates for the full FILL / wght /
        // GRAD / opsz axis set. Regular text stays on the SDL3_ttf path.
        // The capability flag stays true because, from the app's POV, axes
        // do work on this renderer — Material Symbols install() therefore
        // doesn't emit its "axes ignored" warning.
        TextRendererCapabilities.supportsFontVariations = true
    }

    private val fFreeTypeIcons = FreeTypeIcons()
    private val fFreeTypeText = FreeTypeText()

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

    // Cached TTF_Font handles keyed by (family, logical pixel size). family is
    // null for the default (Roboto); non-null for registered IconFont entries.
    private val fFontCache = mutableMapOf<Pair<String?, Int>, COpaquePointer>()

    // Per-family bundled font bytes — allocated on the native heap once,
    // shared by every TTF_OpenFontIO opened from that family (the IO closes
    // with the font but the underlying mem must outlive every font opened
    // against it, so we keep them all until destroy()).
    private val fFontMem = mutableMapOf<String?, Pair<CPointer<ByteVar>, Int>>()
    // Families we've already tried and failed to resolve — cached so we don't
    // keep retrying every frame.
    private val fMissingFamilies = mutableSetOf<String>()

    // Cached glyph textures: (family, text, fontSize, packedARGB) → texture.
    private data class TextureKey(val family: String?, val text: String, val fontSize: Int, val color: Int)
    private data class CachedTexture(val tex: COpaquePointer, val w: Int, val h: Int)
    private val fTextureCache = mutableMapOf<TextureKey, CachedTexture>()

    // Cached measured widths in LOGICAL points keyed by (family, text, size).
    private val fWidthCache = mutableMapOf<Triple<String?, String, Int>, Int>()

    /* Returns false if TTF couldn't init. */
    fun init(): Boolean {
        if (!TTF_Init()) {
            println("TTF_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }
        // Register the default font's bytes with FreeTypeText so we can
        // route text-with-variations through it (the SDL3_ttf path can't
        // honour wght / wdth axes on its own).
        defaultFontBytes()?.let { fFreeTypeText.registerFontBytes("", it) }
        return true
    }

    fun destroy() {
        for (v in fTextureCache.values) SDL_DestroyTexture(v.tex.reinterpret())
        fTextureCache.clear()
        for (f in fFontCache.values) TTF_CloseFont(f.reinterpret())
        fFontCache.clear()
        for ((vMem, _) in fFontMem.values) nativeHeap.free(vMem)
        fFontMem.clear()
        fMissingFamilies.clear()
        fFreeTypeIcons.destroy()
        fFreeTypeText.destroy()
        TTF_Quit()
    }

    val textMeasurer: TextMeasurer = object : TextMeasurer {
        // When variations are present and the family is the default
        // (no IconFont match), route width / line-height through the
        // FreeText path so the wght axis the layout sees matches what
        // gets painted. Without this, layout uses unweighted glyph
        // widths and the paint then renders wider weighted glyphs,
        // producing visible right-edge clipping.
        override fun measure(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>?): IntSize {
            val vWrap = wrap(inText, inFontSize, inMaxWidth, inFontFamily, inFontVariations)
            val vWidth = if (vWrap.lines.isEmpty()) 0
                         else vWrap.lines.maxOf { measureWidth(it, inFontSize, inFontFamily, inFontVariations) }
            val vLine = lineHeight(inFontSize, inFontFamily, inFontVariations).toInt().coerceAtLeast(1)
            return IntSize(vWidth, vLine * vWrap.lines.size.coerceAtLeast(1))
        }

        override fun wrap(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>?): WrappedText =
            wrapTextWithStarts(inText, inFontSize, inMaxWidth, inFontFamily, inFontVariations)

        override fun lineHeight(inFontSize: Int, inFontFamily: String?, inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>?): Float {
            if (shouldUseFreeTypeText(inFontFamily, inFontVariations)) {
                return fFreeTypeText.lineHeight(inFontFamily, inFontSize, inFontVariations!!)
            }
            val vFont = getFont(inFontFamily, inFontSize) ?: return inFontSize * 1.3f
            // TTF_GetFontHeight returns physical pixels (we opened the
            // font at fontSize * DPR). Convert back to logical.
            return (TTF_GetFontHeight(vFont.reinterpret()).toFloat() / fDpr).coerceAtLeast(1f)
        }
    }

    /* Variations on the default family go through the FreeType text
       path; icon families stay on FreeTypeIcons; everything else falls
       through to SDL3_ttf. */
    private fun shouldUseFreeTypeText(
        inFontFamily: String?,
        inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>?,
    ): Boolean {
        if (inFontVariations.isNullOrEmpty()) return false
        // IconFont families have their own variable-axis path.
        if (inFontFamily != null && fFreeTypeIcons.hasFamily(inFontFamily)) return false
        return fFreeTypeText.hasFamily(inFontFamily)
    }

    /* Greedy soft-wrap that mirrors the Skia renderer's algorithm so the
       cross-platform behaviour stays identical. Hard lines on '\n'; long
       lines split at whitespace, ultra-long words split mid-word. */
    private fun wrapTextWithStarts(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String? = null, inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>? = null): WrappedText {
        if (inText.isEmpty()) return WrappedText(listOf(""), intArrayOf(0))
        val vLines = mutableListOf<String>()
        val vStarts = mutableListOf<Int>()
        val vUnbounded = inMaxWidth >= Int.MAX_VALUE / 2

        var vHardStart = 0
        while (vHardStart <= inText.length) {
            val vNl = inText.indexOf('\n', vHardStart)
            val vHardEnd = if (vNl < 0) inText.length else vNl
            val vHard = inText.substring(vHardStart, vHardEnd)
            if (vUnbounded || vHard.isEmpty() || measureWidth(vHard, inFontSize, inFontFamily, inFontVariations) <= inMaxWidth) {
                vLines.add(vHard); vStarts.add(vHardStart)
            } else {
                wrapHardLine(vHard, inFontSize, inMaxWidth, vHardStart, vLines, vStarts, inFontFamily, inFontVariations)
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
        inFontFamily: String? = null,
        inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>? = null,
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
            if (measureWidth(vCandidate, inFontSize, inFontFamily, inFontVariations) <= inMaxWidth) {
                vCurrent.append(vWord)
            } else {
                if (vCurrent.isNotEmpty()) {
                    outLines.add(vCurrent.toString())
                    outStarts.add(inBaseOffset + vLineStartInHard)
                    vLineStartInHard += vCurrent.length
                    vCurrent = StringBuilder()
                }
                if (measureWidth(vWord, inFontSize, inFontFamily, inFontVariations) > inMaxWidth) {
                    val vSub = StringBuilder()
                    for (ch in vWord) {
                        if (measureWidth(vSub.toString() + ch, inFontSize, inFontFamily, inFontVariations) > inMaxWidth) {
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

    // Tab stops are rendered as a fixed run of spaces so a literal '\t'
    // (e.g. in the API body editor) shows as indentation instead of a
    // font-dependent, often zero-width glyph. Expansion is width-only /
    // draw-only — callers keep the original '\t' so the text's character
    // indices (cursor / selection) stay correct.
    private fun expandTabs(inText: String): String =
        if ('\t' in inText) inText.replace("\t", " ".repeat(TextLayoutConfig.tabWidth)) else inText

    /* Returns LOGICAL-point width. The font was opened at fontSize*DPR
       so TTF_GetStringSize reports physical pixels — divide by DPR to
       get back to logical. */
    private fun measureWidth(
        inText: String,
        inFontSize: Int,
        inFontFamily: String? = null,
        inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>? = null,
    ): Int {
        if (inText.isEmpty()) return 0
        // Tabs → spaces for measurement (width-only; original '\t' kept).
        val vText = expandTabs(inText)
        // When variations are present, defer to FreeTypeText so the
        // measured width matches the (weighted) glyphs we'll paint.
        if (shouldUseFreeTypeText(inFontFamily, inFontVariations)) {
            return fFreeTypeText.measureString(inFontFamily, vText, inFontSize, inFontVariations!!)
        }
        // Key tab-containing text by the current tab width (see SkiaTextRenderer).
        val vKey = Triple(inFontFamily, if ('\t' in inText) "${TextLayoutConfig.tabWidth} $inText" else inText, inFontSize)
        fWidthCache[vKey]?.let { return it }
        val vFont = getFont(inFontFamily, inFontSize) ?: run {
            val vEst = (vText.length * inFontSize * 0.6f).toInt()
            fWidthCache[vKey] = vEst
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
            if (TTF_GetStringSize(vFont.reinterpret(), vText, 0u, vW.ptr, vH.ptr)) {
                vPhys = vW.value
            }
        }
        val vLogical = (vPhys / fDpr).toInt()
        fWidthCache[vKey] = vLogical
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
        inFontFamily: String? = null,
        inFontVariations: List<androidx.compose.ui.text.font.FontVariation.Setting>? = null,
        inSpans: List<Range<SpanStyle>>? = null,
        inTextStart: Int = 0,
    ) {
        if (inText.isEmpty()) return
        val vRenderer = backend.renderer ?: return

        // Icon-font path: anything registered in IconFont gets routed through
        // FreeType so variable-font axes work. We treat the text as a single
        // codepoint per icon (Material Symbols are all BMP single chars).
        if (inFontFamily != null && fFreeTypeIcons.hasFamily(inFontFamily)) {
            val vCodepoint = inText.codePointAtSafe(0)
            val vDrew = fFreeTypeIcons.drawGlyph(
                inSdlRenderer = vRenderer,
                inFamily = inFontFamily,
                inCodepoint = vCodepoint,
                inPixelSize = inFontSize,
                inColor = inColor,
                inVariations = inFontVariations ?: emptyList(),
                inBoxX = inX,
                inBoxY = inY,
                inBoxW = inBoxWidth,
                inBoxH = inBoxHeight,
                inDpr = fDpr,
            )
            if (vDrew) return
            // Fall through to SDL3_ttf if the FreeType path couldn't draw
            // (missing family, no glyph, etc.) so we at least show *something*.
        }

        // Tabs → spaces for non-icon text (draw-only; callers keep '\t').
        val vText = expandTabs(inText)

        // Variable-axis text path: when the caller passed any variations
        // (typically wght from a SpanStyle.fontWeight), rasterise through
        // FreeType so the axis interpolation actually applies.
        if (shouldUseFreeTypeText(inFontFamily, inFontVariations)) {
            val vWidth = fFreeTypeText.measureString(inFontFamily, vText, inFontSize, inFontVariations!!)
            val vHeight = fFreeTypeText.lineHeight(inFontFamily, inFontSize, inFontVariations).toInt().coerceAtLeast(1)
            val vPenX = when (inAlign) {
                TextAlign.Start  -> inX.toFloat()
                TextAlign.Center -> inX + (inBoxWidth - vWidth) / 2f
                TextAlign.End    -> inX + (inBoxWidth - vWidth).toFloat()
                else             -> inX.toFloat()
            }
            val vPenY = inY + (inBoxHeight - vHeight) / 2f
            val vDrew = fFreeTypeText.drawString(
                inSdlRenderer = vRenderer,
                inFamily = inFontFamily,
                inText = vText,
                inPixelSize = inFontSize,
                inColor = inColor,
                inVariations = inFontVariations,
                inX = vPenX,
                inY = vPenY,
                inDpr = fDpr,
            )
            if (vDrew) return
            // Otherwise fall through to SDL3_ttf with default styling.
        }

        // Per-span colours: split this (already-wrapped) line into same-colour
        // segments — mapped from original-text indices via inTextStart — and
        // blit each as its own texture at its prefix x. Tabs expand for both the
        // prefix measure and the texture so offsets and glyphs agree.
        if (inSpans != null) {
            val vLineW = measureWidth(inText, inFontSize, inFontFamily).toFloat()
            val vPenX0 = when (inAlign) {
                TextAlign.Start  -> inX.toFloat()
                TextAlign.Center -> inX + (inBoxWidth - vLineW) / 2f
                TextAlign.End    -> inX + (inBoxWidth - vLineW)
                else             -> inX.toFloat()
            }
            fun snap(inV: Float): Float = kotlin.math.round(inV * fDpr) / fDpr
            // O(spans + line length) colour runs, instead of an O(chars × spans)
            // per-character span scan, so a highlighted body with thousands of
            // spans stays cheap per visible line.
            for (vRun in lineColorRuns(inText, inTextStart, inSpans, inColor)) {
                val vSeg = expandTabs(inText.substring(vRun.start, vRun.end))
                val vSegX = vPenX0 + measureWidth(inText.substring(0, vRun.start), inFontSize, inFontFamily).toFloat()
                val vCachedSeg = getOrCreateTexture(inFontFamily, vSeg, inFontSize, vRun.color) ?: continue
                val vLogW = vCachedSeg.w / fDpr
                val vLogH = vCachedSeg.h / fDpr
                val vPenY = inY + (inBoxHeight - vLogH) / 2f
                memScoped {
                    val vDst = alloc<SDL_FRect>()
                    vDst.x = snap(vSegX)
                    vDst.y = snap(vPenY)
                    vDst.w = vLogW
                    vDst.h = vLogH
                    SDL_RenderTexture(vRenderer.reinterpret(), vCachedSeg.tex.reinterpret(), null, vDst.ptr)
                }
            }
            return
        }

        val vCached = getOrCreateTexture(inFontFamily, vText, inFontSize, inColor) ?: return

        // Texture dimensions are physical pixels (rasterised at fontSize *
        // DPR). Convert to logical for the dst rect so SDL_SetRenderScale's
        // stretch brings it back to the same pixel size — i.e. 1:1, crisp.
        val vLogW = vCached.w / fDpr
        val vLogH = vCached.h / fDpr

        val vPenX = when (inAlign) {
            TextAlign.Start  -> inX.toFloat()
            TextAlign.Center -> inX + (inBoxWidth - vLogW) / 2f
            TextAlign.End    -> inX + (inBoxWidth - vLogW)
                else             -> inX.toFloat()
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
        inFontFamily: String?,
        inText: String,
        inFontSize: Int,
        inColor: ComposeColor,
    ): CachedTexture? {
        val vKey = TextureKey(inFontFamily, inText, inFontSize, inColor.toArgb())
        fTextureCache[vKey]?.let { return it }

        val vRenderer = backend.renderer ?: return null
        val vFont = getFont(inFontFamily, inFontSize) ?: return null
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
       is (family, logical size) — setDpr clears the cache when DPR changes.

       Default family (null) tries the bundled Roboto first, then common
       system fonts. A non-null family looks up bytes in the IconFont
       registry and opens those — falling back to null when not registered. */
    private fun getFont(inFamily: String?, inSize: Int): COpaquePointer? {
        val vKey = inFamily to inSize
        fFontCache[vKey]?.let { return it }

        val vPhysicalSize = (inSize * fDpr).coerceAtLeast(1f)

        if (inFamily == null) {
            openFontFromBytes(null, ::defaultFontBytes, vPhysicalSize)?.let {
                fFontCache[vKey] = it
                return it
            }
            val vSystemPaths = listOf(
                "C:\\Windows\\Fonts\\segoeui.ttf",
                "C:\\Windows\\Fonts\\arial.ttf",
            )
            for (path in vSystemPaths) {
                val vFont = TTF_OpenFont(path, vPhysicalSize)
                if (vFont != null) {
                    fFontCache[vKey] = vFont
                    return vFont
                }
            }
            println("Sdl3TextRenderer: no usable font found for size $inSize")
            return null
        }

        if (inFamily in fMissingFamilies) return null
        val vBytes = IconFont.bytesFor(inFamily)
        if (vBytes == null) {
            println("Sdl3TextRenderer: IconFont '$inFamily' not registered")
            fMissingFamilies += inFamily
            return null
        }
        val vFont = openFontFromBytes(inFamily, { vBytes }, vPhysicalSize)
        if (vFont != null) fFontCache[vKey] = vFont
        return vFont
    }

    private fun defaultFontBytes(): ByteArray? = loadComposeResourceBytes("font/NotoSans.ttf")

    /* Lazily uploads a family's bytes into the native heap (once per family),
       then opens a fresh TTF_Font on a new SDL_IOFromConstMem stream. Each
       size of a family gets its own font handle but shares the same byte
       buffer; the buffer lives until destroy(). */
    private fun openFontFromBytes(
        inFamily: String?,
        inBytesProvider: () -> ByteArray?,
        inPhysicalPt: Float,
    ): COpaquePointer? {
        val vSlot = fFontMem.getOrPut(inFamily) {
            val vBytes = inBytesProvider() ?: return null
            if (vBytes.isEmpty()) return null
            val vMem = nativeHeap.allocArray<ByteVar>(vBytes.size)
            vBytes.usePinned { vPinned ->
                platform.posix.memcpy(vMem, vPinned.addressOf(0), vBytes.size.convert())
            }
            vMem to vBytes.size
        }
        val vIo = SDL_IOFromConstMem(vSlot.first, vSlot.second.convert()) ?: return null
        return TTF_OpenFontIO(vIo.reinterpret(), true, inPhysicalPt)
    }

    private fun ComposeColor.toArgb(): Int =
        (a8 shl 24) or (r8 shl 16) or (g8 shl 8) or b8

    /* Decodes the codepoint at the given char index, handling UTF-16
       surrogate pairs for supplementary-plane characters. Material Symbols
       icons are BMP so the surrogate path rarely fires, but Icon supports
       arbitrary codepoints (codepointToString returns a surrogate pair for
       supplementary). */
    private fun String.codePointAtSafe(inIndex: Int): Int {
        val vHigh = this[inIndex].code
        if (vHigh in 0xD800..0xDBFF && inIndex + 1 < length) {
            val vLow = this[inIndex + 1].code
            if (vLow in 0xDC00..0xDFFF) {
                return 0x10000 + ((vHigh - 0xD800) shl 10) + (vLow - 0xDC00)
            }
        }
        return vHigh
    }
}
