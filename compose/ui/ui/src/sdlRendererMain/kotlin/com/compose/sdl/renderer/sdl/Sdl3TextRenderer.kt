package com.compose.sdl.renderer.sdl

import com.compose.sdl.*
import com.compose.sdl.icons.IconFont

import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.sdl.graphics.r8
import com.compose.sdl.graphics.g8
import com.compose.sdl.graphics.b8
import com.compose.sdl.graphics.a8
import com.compose.sdl.text.TextMeasurer
import com.compose.sdl.text.TextRendererCapabilities
import com.compose.sdl.text.WrappedText
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import com.compose.sdl.text.ColorRun
import com.compose.sdl.text.lineColorRuns
import com.compose.sdl.text.resolveRunPx
import com.compose.sdl.text.runVariations
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
import sdl3.SDL_SetTextureAlphaMod
import sdl3.SDL_SetTextureColorMod
import sdl3.SDL_BLENDMODE_BLEND
import sdl3.SDL_RenderFillRect
import sdl3.SDL_SetRenderDrawBlendMode
import sdl3.SDL_SetRenderDrawColor
import sdl3_ttf.TTF_CloseFont
import sdl3_ttf.TTF_GetFontAscent
import sdl3_ttf.TTF_GetFontHeight
import sdl3_ttf.TTF_GetStringSize
import sdl3_ttf.TTF_Init
import sdl3_ttf.TTF_OpenFont
import sdl3_ttf.TTF_OpenFontIO
import sdl3_ttf.TTF_Quit
import sdl3_ttf.TTF_RenderText_Blended
import sdl3_ttf.TTF_SetFontAxisValue
import sdl3_ttf.TTF_SetFontStyle
import sdl3_ttf.TTF_StringToTag
import sdl3.SDL_IOFromConstMem
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Density

// ==================
// MARK: Sdl3TextRenderer (mingwX64 fallback)
// ==================

// Cache caps. Textures: ~a few hundred distinct visible strings is plenty for a
// screenful of UI; evicted entries destroy their GPU texture and re-rasterise on
// next use. Widths: pure ints, capped by clear (see fWidthCache).
private const val kTextureCacheMax: Int = 768
private const val kWidthCacheMax: Int = 16384

// SDL_ttf TTF_STYLE_* bits (values from SDL_ttf.h). Set per rasterise/measure
// call — synthetic italic and underline/strikethrough bake into the cached
// white-glyph texture (SDL_ttf computes the decoration metrics) and get tinted
// with the run colour at blit time like the glyphs themselves.
private const val kStyleItalic: Int = 0x02
private const val kStyleUnderline: Int = 0x04
private const val kStyleStrikethrough: Int = 0x08

/* Text via SDL3_ttf. Caches TTF_Font per size, and a per-(text, color,
   fontSize) SDL_Texture so repeated frames don't re-rasterise the same
   string. Implements TextMeasurer so the common layout pass agrees
   with what we paint. */
internal class Sdl3TextRenderer(private val backend: SDL3Backend) {

    init {
        // Regular text applies variable-font axes (wght, etc.) through the
        // forked SDL3_ttf axis API (TTF_SetFontAxisValue). Icon-font draws still
        // route through FreeType (FreeTypeIcons) for the full FILL / wght / GRAD
        // / opsz set. Either way axes work, so the capability flag stays true
        // (Material Symbols install() skips its "axes ignored" warning).
        TextRendererCapabilities.supportsFontVariations = true
    }

    private val fFreeTypeIcons = FreeTypeIcons()

    // Density for FontVariation.Setting.toVariationValue — only TextUnit axes
    // (e.g. opsz in sp) consult it; wght and friends ignore it. Glyphs are
    // rasterised at physical px already, so a 1:1 density is correct here.
    private val fVarDensity = Density(1f)

    // HiDPI scale. Fonts are opened at fontSize * DPR pixels so the
    // rasterised glyph texture matches the physical pixel count of the
    // logical-size dst rect after SDL_SetRenderScale stretches it.
    // Measurement results are divided back by DPR so layout still works
    // in logical points.
    private var fDpr: Float = 1f

    // Exposed for Sdl3Canvas — Sp-valued span sizes resolve through the same
    // density the paragraph resolved its base size with (see resolveRunPx).
    internal val dpr: Float get() = fDpr

    fun setDpr(inDpr: Float) {
        if (inDpr == fDpr) return
        fDpr = inDpr
        // Invalidate everything keyed off the old DPR-baked sizes.
        fTextureCache.clear()  // onEvict destroys the SDL textures
        for (f in fFontCache.values) TTF_CloseFont(f.reinterpret())
        fFontCache.clear()
        fWidthCache.clear()
    }

    // Cached TTF_Font handles keyed by (family, logical pixel size, variations).
    // family is null for the default; non-null for registered IconFont entries.
    // Variations are baked into the handle (TTF_SetFontAxisValue mutates the
    // font), so each distinct axis set gets its own handle.
    private data class FontKey(val family: String?, val size: Int, val variations: String)
    private val fFontCache = mutableMapOf<FontKey, COpaquePointer>()

    /* A stable key for a variation set — also the value serialised for cache
       lookups. Empty when there are no variations. */
    private fun variationsKey(inVariations: List<FontVariation.Setting>?): String =
        if (inVariations.isNullOrEmpty()) ""
        else inVariations.joinToString(",") { "${it.axisName}=${it.toVariationValue(fVarDensity)}" }

    /* Applies each variation axis to an open TTF_Font via the forked axis API.
       TTF_StringToTag turns "wght" into the OpenType tag; unsupported axes are
       ignored by SDL3_ttf (returns false), so passing e.g. wght to a static
       font is a harmless no-op. */
    private fun applyAxes(inFont: COpaquePointer, inVariations: List<FontVariation.Setting>?) {
        if (inVariations.isNullOrEmpty()) return
        for (vSetting in inVariations) {
            TTF_SetFontAxisValue(
                inFont.reinterpret(),
                TTF_StringToTag(vSetting.axisName),
                vSetting.toVariationValue(fVarDensity),
            )
        }
    }

    // Per-family bundled font bytes — allocated on the native heap once,
    // shared by every TTF_OpenFontIO opened from that family (the IO closes
    // with the font but the underlying mem must outlive every font opened
    // against it, so we keep them all until destroy()).
    private val fFontMem = mutableMapOf<String?, Pair<CPointer<ByteVar>, Int>>()
    // Families we've already tried and failed to resolve — cached so we don't
    // keep retrying every frame.
    private val fMissingFamilies = mutableSetOf<String>()

    // Cached glyph textures: (family, text, fontSize, variations) → texture.
    // Glyphs are rasterised WHITE (RGB=255, A=coverage); the tint colour and
    // alpha are applied at blit time via SDL_SetTextureColorMod/AlphaMod, so
    // colour/alpha animations don't grow the cache or re-rasterise.
    private data class TextureKey(val family: String?, val text: String, val fontSize: Int, val variations: String, val style: Int)
    private data class CachedTexture(val tex: COpaquePointer, val w: Int, val h: Int)
    // LRU-capped: every unique string ever rendered used to keep a GPU texture
    // alive for the whole session (TextField keystrokes, response bodies, …).
    private val fTextureCache = LruCache<TextureKey, CachedTexture>(kTextureCacheMax) {
        SDL_DestroyTexture(it.tex.reinterpret())
    }

    /* Applies the tint at blit time — the cached texture is white glyphs with
       coverage alpha; modulation multiplies per-channel, exactly what baking
       the colour into the surface used to do. */
    private fun applyTint(inTex: COpaquePointer, inColor: ComposeColor) {
        SDL_SetTextureColorMod(inTex.reinterpret(), inColor.r8.toUByte(), inColor.g8.toUByte(), inColor.b8.toUByte())
        SDL_SetTextureAlphaMod(inTex.reinterpret(), inColor.a8.toUByte())
    }

    // Cached measured widths in LOGICAL points keyed by (family, text, size, variations).
    // Cap-and-clear (not LRU): lookups stay a single hash op on the hot measure
    // path, and re-measuring after a rare full clear is cheap.
    private data class WidthKey(val family: String?, val text: String, val fontSize: Int, val variations: String, val style: Int)
    private val fWidthCache = mutableMapOf<WidthKey, Int>()

    /* Returns false if TTF couldn't init. */
    fun init(): Boolean {
        if (!TTF_Init()) {
            println("TTF_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }
        return true
    }

    fun destroy() {
        fTextureCache.clear()  // onEvict destroys the SDL textures
        for (f in fFontCache.values) TTF_CloseFont(f.reinterpret())
        fFontCache.clear()
        for ((vMem, _) in fFontMem.values) nativeHeap.free(vMem)
        fFontMem.clear()
        fMissingFamilies.clear()
        fFreeTypeIcons.destroy()
        TTF_Quit()
    }

    val textMeasurer: TextMeasurer = object : TextMeasurer {
        // Measurement applies the same axes as paint (via getFont), so layout
        // sees the weighted glyph widths and there's no right-edge clipping.
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
            val vFont = getFont(inFontFamily, inFontSize, inFontVariations) ?: return inFontSize * 1.3f
            // TTF_GetFontHeight returns physical pixels (we opened the
            // font at fontSize * DPR). Convert back to logical.
            return (TTF_GetFontHeight(vFont.reinterpret()).toFloat() / fDpr).coerceAtLeast(1f)
        }
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
        inStyle: Int = 0,
    ): Int {
        if (inText.isEmpty()) return 0
        // Tabs → spaces for measurement (width-only; original '\t' kept).
        val vText = expandTabs(inText)
        // Axes are baked into the font handle (getFont applies them), so
        // TTF_GetStringSize reports the weighted width and layout matches paint.
        // Key tab-containing text by the current tab width (see SkiaTextRenderer).
        val vKeyText = if ('\t' in inText) "${TextLayoutConfig.tabWidth} $inText" else inText
        val vKey = WidthKey(inFontFamily, vKeyText, inFontSize, variationsKey(inFontVariations), inStyle)
        fWidthCache[vKey]?.let { return it }
        val vFont = getFont(inFontFamily, inFontSize, inFontVariations) ?: run {
            val vEst = (vText.length * inFontSize * 0.6f).toInt()
            fWidthCache[vKey] = vEst
            return vEst
        }
        var vPhys = 0
        memScoped {
            val vW = alloc<IntVar>()
            val vH = alloc<IntVar>()
            // Style set per call (handles are shared) — synthetic italic widens
            // the reported box, so styled measure matches the styled texture.
            TTF_SetFontStyle(vFont.reinterpret(), inStyle.toUInt())
            // Length = 0 → SDL_ttf calls strlen on the UTF-8 string. Don't
            // pass inText.length: that's UTF-16 code-unit count, but the C
            // side wants byte count. Non-ASCII chars (e.g. em-dash → 3
            // UTF-8 bytes) would otherwise truncate the tail of the string.
            if (TTF_GetStringSize(vFont.reinterpret(), vText, 0u, vW.ptr, vH.ptr)) {
                vPhys = vW.value
            }
            TTF_SetFontStyle(vFont.reinterpret(), 0u)
        }
        val vLogical = (vPhys / fDpr).toInt()
        if (fWidthCache.size >= kWidthCacheMax) fWidthCache.clear()
        fWidthCache[vKey] = vLogical
        return vLogical
    }

    /* Ascent of the font at inFontSize in LOGICAL points — used to align
       mixed-size runs on a common baseline. */
    private fun fontAscent(
        inFontSize: Int,
        inFontFamily: String?,
        inFontVariations: List<FontVariation.Setting>? = null,
    ): Float {
        val vFont = getFont(inFontFamily, inFontSize, inFontVariations) ?: return inFontSize * 0.8f
        return TTF_GetFontAscent(vFont.reinterpret()).toFloat() / fDpr
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
        inItalic: Boolean = false,
        inUnderline: Boolean = false,
        inLineThrough: Boolean = false,
    ) {
        if (inText.isEmpty()) return
        val vRenderer = backend.renderer ?: return
        // Paragraph-level style bits (TextStyle.fontStyle / paint textDecoration).
        val vBaseStyle =
            (if (inItalic) kStyleItalic else 0) or
            (if (inUnderline) kStyleUnderline else 0) or
            (if (inLineThrough) kStyleStrikethrough else 0)

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

        // Per-span styles: split this (already-wrapped) line into same-style
        // segments — mapped from original-text indices via inTextStart — and
        // blit each as its own texture at its prefix x. Tabs expand for both the
        // prefix measure and the texture so offsets and glyphs agree.
        if (inSpans != null) {
            // O(spans + line length) style runs (colour + weight + italic +
            // background + decoration + size), instead of an O(chars × spans)
            // per-character scan.
            val vRuns = lineColorRuns(
                inText, inTextStart, inSpans, inColor,
                inBaseItalic = inItalic,
                inBaseUnderline = inUnderline,
                inBaseLineThrough = inLineThrough,
            )
            // Shared resolution helpers (ColorRun.kt) — the SAME code SdlParagraph
            // measures layout with, so painted runs land inside the measured box.
            fun runVars(inRun: ColorRun): List<androidx.compose.ui.text.font.FontVariation.Setting>? =
                runVariations(inRun, inFontVariations)
            fun runSeg(inRun: ColorRun): String = expandTabs(inText.substring(inRun.start, inRun.end))
            fun runPx(inRun: ColorRun): Int = resolveRunPx(inRun, inFontSize, fDpr)
            fun runStyle(inRun: ColorRun): Int =
                (if (inRun.italic) kStyleItalic else 0) or
                (if (inRun.underline) kStyleUnderline else 0) or
                (if (inRun.lineThrough) kStyleStrikethrough else 0)
            // Line width = sum of each run's width at ITS style (for alignment).
            var vLineW = 0f
            for (vRun in vRuns) vLineW += measureWidth(runSeg(vRun), runPx(vRun), inFontFamily, runVars(vRun), runStyle(vRun)).toFloat()
            val vPenX0 = when (inAlign) {
                TextAlign.Start  -> inX.toFloat()
                TextAlign.Center -> inX + (inBoxWidth - vLineW) / 2f
                TextAlign.End    -> inX + (inBoxWidth - vLineW)
                else             -> inX.toFloat()
            }
            fun snap(inV: Float): Float = kotlin.math.round(inV * fDpr) / fDpr
            // Common baseline: centre the line's TALLEST run cell in the line box
            // (the box the canvas passes IS that tall for mixed-size lines), then
            // sit every run's baseline on it. Without this, a bigger/smaller run
            // would centre its own texture and float off the baseline. For
            // uniform lines this reduces to centring the base cell — identical
            // to the non-span path.
            var vMaxCellH = textMeasurer.lineHeight(inFontSize, inFontFamily, inFontVariations)
            var vMaxAscent = fontAscent(inFontSize, inFontFamily, inFontVariations)
            for (vRun in vRuns) {
                val vPx = runPx(vRun)
                if (vPx != inFontSize) {
                    val vVars = runVars(vRun)
                    val vCell = textMeasurer.lineHeight(vPx, inFontFamily, vVars)
                    if (vCell > vMaxCellH) vMaxCellH = vCell
                    val vAsc = fontAscent(vPx, inFontFamily, vVars)
                    if (vAsc > vMaxAscent) vMaxAscent = vAsc
                }
            }
            val vBaselineY = inY + (inBoxHeight - vMaxCellH) / 2f + vMaxAscent
            // Walk runs left to right, advancing by each run's styled advance so
            // a bold/resized run pushes the following runs over by the right amount.
            var vRunX = vPenX0
            for (vRun in vRuns) {
                val vVars = runVars(vRun)
                val vSeg = runSeg(vRun)
                val vPx = runPx(vRun)
                val vStyle = runStyle(vRun)
                val vAdvance = measureWidth(vSeg, vPx, inFontFamily, vVars, vStyle).toFloat()
                // SpanStyle.background — fill the run's slice of the line band
                // behind the glyphs.
                if (vRun.background != ComposeColor.Unspecified && vRun.background.alpha > 0f) {
                    memScoped {
                        val vRect = alloc<SDL_FRect>()
                        vRect.x = snap(vRunX)
                        vRect.y = inY.toFloat()
                        vRect.w = vAdvance
                        vRect.h = inBoxHeight.toFloat()
                        SDL_SetRenderDrawBlendMode(vRenderer.reinterpret(), SDL_BLENDMODE_BLEND)
                        SDL_SetRenderDrawColor(
                            vRenderer.reinterpret(),
                            vRun.background.r8.toUByte(), vRun.background.g8.toUByte(),
                            vRun.background.b8.toUByte(), vRun.background.a8.toUByte(),
                        )
                        SDL_RenderFillRect(vRenderer.reinterpret(), vRect.ptr)
                    }
                }
                val vCachedSeg = getOrCreateTexture(inFontFamily, vSeg, vPx, vVars, vStyle)
                if (vCachedSeg != null) {
                    applyTint(vCachedSeg.tex, vRun.color)
                    val vLogW = vCachedSeg.w / fDpr
                    val vLogH = vCachedSeg.h / fDpr
                    val vPenY = vBaselineY - fontAscent(vPx, inFontFamily, vVars)
                    memScoped {
                        val vDst = alloc<SDL_FRect>()
                        vDst.x = snap(vRunX)
                        vDst.y = snap(vPenY)
                        vDst.w = vLogW
                        vDst.h = vLogH
                        SDL_RenderTexture(vRenderer.reinterpret(), vCachedSeg.tex.reinterpret(), null, vDst.ptr)
                    }
                }
                vRunX += vAdvance
            }
            return
        }

        val vCached = getOrCreateTexture(inFontFamily, vText, inFontSize, inFontVariations, vBaseStyle) ?: return
        applyTint(vCached.tex, inColor)

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
        inFontVariations: List<FontVariation.Setting>? = null,
        inStyle: Int = 0,
    ): CachedTexture? {
        val vKey = TextureKey(inFontFamily, inText, inFontSize, variationsKey(inFontVariations), inStyle)
        fTextureCache[vKey]?.let { return it }

        val vRenderer = backend.renderer ?: return null
        val vFont = getFont(inFontFamily, inFontSize, inFontVariations) ?: return null
        val vTex = memScoped {
            // White glyphs — tint applied per-blit via texture colour/alpha mod.
            val vColor = alloc<SDL_Color>()
            vColor.r = 255u
            vColor.g = 255u
            vColor.b = 255u
            vColor.a = 255u
            // Style set per call (handles are shared): synthetic italic shear +
            // underline/strikethrough bake into the (cached) texture.
            TTF_SetFontStyle(vFont.reinterpret(), inStyle.toUInt())
            val vSurface = TTF_RenderText_Blended(
                vFont.reinterpret(),
                inText,
                // 0 → strlen on the UTF-8 string (see measureWidth).
                0u,
                vColor.readValue(),
            )
            TTF_SetFontStyle(vFont.reinterpret(), 0u)
            if (vSurface == null) return@memScoped null
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
    private fun getFont(
        inFamily: String?,
        inSize: Int,
        inVariations: List<FontVariation.Setting>? = null,
    ): COpaquePointer? {
        val vKey = FontKey(inFamily, inSize, variationsKey(inVariations))
        fFontCache[vKey]?.let { return it }

        val vPhysicalSize = (inSize * fDpr).coerceAtLeast(1f)

        // Open a fresh handle (each variation set gets its own, since axes are
        // baked in), then apply the requested axes and cache it.
        val vFont: COpaquePointer? =
            if (inFamily == null) {
                openFontFromBytes(null, ::defaultFontBytes, vPhysicalSize)
                    ?: run {
                        var vSys: COpaquePointer? = null
                        for (path in listOf("C:\\Windows\\Fonts\\segoeui.ttf", "C:\\Windows\\Fonts\\arial.ttf")) {
                            val vOpened = TTF_OpenFont(path, vPhysicalSize)
                            if (vOpened != null) { vSys = vOpened; break }
                        }
                        if (vSys == null) println("Sdl3TextRenderer: no usable font found for size $inSize")
                        vSys
                    }
            } else if (inFamily in fMissingFamilies) {
                null
            } else {
                val vBytes = IconFont.bytesFor(inFamily)
                if (vBytes == null) {
                    println("Sdl3TextRenderer: IconFont '$inFamily' not registered")
                    fMissingFamilies += inFamily
                    null
                } else {
                    openFontFromBytes(inFamily, { vBytes }, vPhysicalSize)
                }
            }

        if (vFont != null) {
            applyAxes(vFont, inVariations)
            fFontCache[vKey] = vFont
        }
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
