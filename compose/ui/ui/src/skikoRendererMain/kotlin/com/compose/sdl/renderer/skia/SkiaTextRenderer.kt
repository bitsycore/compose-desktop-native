package com.compose.sdl.renderer.skia

import com.compose.sdl.*
import com.compose.sdl.icons.IconFont

import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.sdl.graphics.r8
import com.compose.sdl.graphics.g8
import com.compose.sdl.graphics.b8
import com.compose.sdl.graphics.a8
import com.compose.sdl.text.ColorRun
import com.compose.sdl.text.TextMeasurer
import com.compose.sdl.text.TextRendererCapabilities
import com.compose.sdl.text.WrappedText
import androidx.compose.ui.graphics.Color as ComposeColorAlias
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import com.compose.sdl.text.lineColorRuns
import com.compose.sdl.text.resolveRunPx
import com.compose.sdl.text.runVariations
import com.compose.sdl.text.spansAffectMetrics
import com.compose.sdl.text.styledLineCellHeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.Typeface
import androidx.compose.ui.text.font.FontVariation as ComposeFontVariation
import org.jetbrains.skia.FontVariation as SkiaFontVariation

// ==================
// MARK: SkiaTextRenderer
// ==================

// Width-cache cap — cap-and-clear like the SDL renderer (Sdl3TextRenderer):
// wrap candidates over large bodies otherwise cache every partial-line
// substring for the whole session. Lookups stay a single hash op.
private const val kWidthCacheMax: Int = 16384

/* Replaces SDL3TextRenderer. Uses Skia for measurement + draw.

   IMPORTANT: in Skiko 0.144.6, the typefaces returned by FontMgr.default
   .matchFamilyStyle on macOS go through SkTypeface_Mac::onCharsToGlyphs,
   which aborts inside sk_malloc_flags as soon as Font.measureText or
   measureTextWidth is called. We work around it by loading the typeface
   from a file we ship next to the binary — file-loaded typefaces don't
   exercise that code path on the macOS Skiko build. The bundled font also
   gives us consistent rendering across macOS / Linux. */
class SkiaTextRenderer {

    init {
        // Publish capabilities so Material Symbols install() etc. can warn
        // when the active renderer can't honour the axes they request.
        TextRendererCapabilities.supportsFontVariations = true
    }

    private val fFontMgr: FontMgr = FontMgr.default

    // Density used to resolve Sp-valued span sizes via resolveRunPx. The Skia
    // canvas is drawn at 1:1 (beginFrame(1f)) so the base fontPx arrives already
    // resolved; this only matters for Sp span sizes (Em spans multiply the base
    // and don't consult density). Mirrors Sdl3TextRenderer.fDpr.
    private var fDensity: Float = 1f
    internal val density: Float get() = fDensity
    fun setDensity(inDensity: Float) { fDensity = inDensity }

    private val fTypeface: Typeface? = pickTypeface()

    /* Typeface key bundles family with a stable string representation of any
       variable-font axis settings, so each variant gets its own makeClone'd
       typeface in the cache. Null variations / null family resolve to the
       default-font slot. */
    private data class TypefaceKey(val family: String?, val variations: String)

    private fun variationsKey(inV: List<ComposeFontVariation.Setting>?): String {
        if (inV.isNullOrEmpty()) return ""
        // Sort by tag so equivalent settings hash the same regardless of
        // caller-supplied order.
        return inV.sortedBy { it.axisName }.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }
    }

    /* Overrides the wght axis (or adds one) on top of caller-supplied variations,
       so per-span FontWeight can be applied without discarding the base font's
       other axes (e.g. a Material Symbols icon that already carries FILL/GRAD/opsz).
       weight=400 short-circuits back to the caller list — the outer drawLine already
       reuses the pre-loaded default font in that case. */
    private fun withWeight(inV: List<ComposeFontVariation.Setting>?, inWeight: Int): List<ComposeFontVariation.Setting> {
        val vOthers = inV?.filter { it.axisName != "wght" } ?: emptyList()
        return vOthers + ComposeFontVariation.Setting("wght", inWeight.toFloat())
    }

    // Per-family + variations typeface cache. Default font (key family=null,
    // variations="") is the one resolved by pickTypeface(); other entries
    // lazily resolve from IconFont.bytesFor() and Typeface.makeClone(...).
    // A null value means lookup failed once — don't retry every frame.
    private val fTypefaceCache = mutableMapOf<TypefaceKey, Typeface?>(TypefaceKey(null, "") to fTypeface)

    // Per-(typefaceKey, fontSize) Skia Font cache (Font wraps a Typeface at a
    // fixed size, glyph cache, hinting, etc.).
    private val fFontCache = mutableMapOf<Pair<TypefaceKey, Int>, Font>()

    // Cache of measured widths per (typefaceKey, text, fontSize).
    private val fWidthCache = mutableMapOf<Triple<TypefaceKey, String, Int>, Int>()

    /* WORKAROUND for Skiko 0.144.6 / macOS — both Font.measureText and
       Font.measureTextWidth call into SkFont::measureText, which goes through
       SkTypeface_Mac::onCharsToGlyphs and aborts inside sk_malloc_flags. Same
       code path even for a file-loaded typeface, because SkFontMgr_Mac wraps
       the file data in a CoreText-backed SkTypeface_Mac. Until Skiko is fixed
       (or we get a FreeType-backed FontMgr) we estimate widths from the char
       class. font.metrics is safe to read — it doesn't trigger glyph lookup. */
    val textMeasurer: TextMeasurer = object : TextMeasurer {
        override fun measure(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<ComposeFontVariation.Setting>?): IntSize {
            val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
            val vFont = getFont(vKey, inFontFamily, inFontVariations, inFontSize)
            val vMetrics = vFont.metrics
            val vLineHeight = (vMetrics.descent - vMetrics.ascent).toInt().coerceAtLeast(1)
            val vWrap = wrap(inText, inFontSize, inMaxWidth, inFontFamily, inFontVariations)
            val vWidth = if (vWrap.lines.isEmpty()) 0
                         else vWrap.lines.maxOf { estimateTextWidth(it, inFontSize, inFontFamily, inFontVariations) }
            return IntSize(vWidth, vLineHeight * vWrap.lines.size.coerceAtLeast(1))
        }

        override fun wrap(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<ComposeFontVariation.Setting>?): WrappedText =
            wrapTextWithStarts(inText, inFontSize, inMaxWidth, inFontFamily, inFontVariations)

        override fun lineHeight(inFontSize: Int, inFontFamily: String?, inFontVariations: List<ComposeFontVariation.Setting>?): Float {
            val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
            val vMetrics = getFont(vKey, inFontFamily, inFontVariations, inFontSize).metrics
            return (vMetrics.descent - vMetrics.ascent).coerceAtLeast(1f)
        }
    }

    fun drawText(
        inCanvas: Canvas,
        inText: String,
        inX: Float,
        inY: Float,
        inBoxWidth: Int,
        inBoxHeight: Int,
        inColor: ComposeColor,
        inFontSize: Int,
        inAlign: TextAlign = TextAlign.Start,
        inSoftWrap: Boolean = true,
        inFontFamily: String? = null,
        inFontVariations: List<ComposeFontVariation.Setting>? = null,
        inSpans: List<Range<SpanStyle>>? = null,
        // Pre-computed wrap (cached on the ProjectLayoutNode by the measure pass) so a
        // huge body isn't re-wrapped here every frame. Null = wrap inline.
        inWrapped: WrappedText? = null,
        // Visible vertical band in canvas coords; lines outside it are skipped.
        inViewTop: Float = Float.NEGATIVE_INFINITY,
        inViewBottom: Float = Float.POSITIVE_INFINITY,
        // Paragraph-level bits folded into every wrapped-line run — mirrors the
        // SDL path so a TextStyle(fontStyle = Italic, textDecoration = Underline)
        // still paints when there are no span styles.
        inBaseItalic: Boolean = false,
        inBaseUnderline: Boolean = false,
        inBaseLineThrough: Boolean = false,
    ) {
        val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
        val vBaseFont = getFont(vKey, inFontFamily, inFontVariations, inFontSize)
        val vBaseMetrics = vBaseFont.metrics
        val vBaseLineHeight = (vBaseMetrics.descent - vBaseMetrics.ascent).coerceAtLeast(1f)
        val vCapHeight = if (vBaseMetrics.capHeight > 0f) vBaseMetrics.capHeight
                         else inFontSize * 0.7f

        // Same wrap algorithm as measureText so layout and rendering produce
        // identical line breakdowns. softWrap = false (e.g. a singleLine field)
        // stays one line and overflows the box width. Reuse the measure pass's
        // cached wrap when supplied.
        val vWrapWidth = if (inSoftWrap) inBoxWidth else Int.MAX_VALUE
        val vWrapped = inWrapped ?: wrapTextWithStarts(inText, inFontSize, vWrapWidth, inFontFamily, inFontVariations)
        val vLines = vWrapped.lines
        // Base-only fast path: no per-span styling AND no paragraph decoration or
        // italic to fold in — keep the original single-line cap-centring for the
        // button case and uniform per-line stacking for multi-line.
        val vHasSpans = inSpans != null
        val vBaseOnly = !vHasSpans && !inBaseItalic && !inBaseUnderline && !inBaseLineThrough
        if (vBaseOnly) {
            val vFastPaint = Paint().apply { color = toSkiaColor(inColor); isAntiAlias = true }
            if (vLines.size == 1 && '\n' !in inText) {
                val vBaseline = inY + (inBoxHeight + vCapHeight) / 2f
                val vPenX = alignX(inX, inBoxWidth, estimateTextWidth(vLines[0], inFontSize, inFontFamily, inFontVariations).toFloat(), inAlign)
                inCanvas.drawString(expandTabs(vLines[0]), vPenX, vBaseline, vBaseFont, vFastPaint)
            } else {
                for ((vIdx, vLine) in vLines.withIndex()) {
                    val vSlotTop = inY + vIdx * vBaseLineHeight
                    if (vSlotTop + vBaseLineHeight < inViewTop || vSlotTop > inViewBottom) continue
                    val vBaseline = vSlotTop + (vBaseLineHeight + vCapHeight) / 2f
                    val vPenX = alignX(inX, inBoxWidth, estimateTextWidth(vLine, inFontSize, inFontFamily, inFontVariations).toFloat(), inAlign)
                    inCanvas.drawString(expandTabs(vLine), vPenX, vBaseline, vBaseFont, vFastPaint)
                }
            }
            vFastPaint.close()
            return
        }

        // Per-line size spans → the line's box height matches the tallest run
        // cell (same styledLineCellHeight the paragraph measured with, so paint
        // stacks lines exactly where layout put them). No metric-affecting spans →
        // uniform base line height.
        val vMetricSpans = spansAffectMetrics(inSpans)
        var vLineY = inY
        for ((vIdx, vLine) in vLines.withIndex()) {
            val vLineStart = vWrapped.lineStarts.getOrElse(vIdx) { 0 }
            val vLineH =
                if (vMetricSpans && inSpans != null) {
                    styledLineCellHeight(
                        vLine, vLineStart, inSpans, inFontSize, fDensity,
                        textMeasurer, inFontFamily, inFontVariations,
                    ).coerceAtLeast(1f)
                } else vBaseLineHeight
            if (vLineY + vLineH < inViewTop || vLineY > inViewBottom) {
                vLineY += vLineH
                continue
            }
            drawLineStyled(
                inCanvas, vLine, vLineStart, inSpans, inColor, inFontSize,
                inFontFamily, inFontVariations,
                inBoxLeft = inX, inBoxTop = vLineY, inBoxWidth = inBoxWidth, inBoxHeight = vLineH,
                inAlign = inAlign,
                inBaseFont = vBaseFont, inBaseCapHeight = vCapHeight,
                inBaseItalic = inBaseItalic,
                inBaseUnderline = inBaseUnderline,
                inBaseLineThrough = inBaseLineThrough,
            )
            vLineY += vLineH
            if (vLineY > inBoxHeight + inY) break
        }
    }

    /* Aligns a line's pen X inside the box by its natural line width. */
    private fun alignX(inX: Float, inBoxWidth: Int, inLineWidth: Float, inAlign: TextAlign): Float =
        when (inAlign) {
            TextAlign.Start  -> inX
            TextAlign.Center -> inX + (inBoxWidth - inLineWidth) / 2f
            TextAlign.End    -> inX + inBoxWidth.toFloat() - inLineWidth
            else             -> inX
        }

    /* Renders ONE wrapped line inside a per-line box, with per-run styling and a
       common baseline. Mirrors Sdl3TextRenderer.drawText's spans path: styled
       prefix advances, alignment width = sum of styled run advances, common
       baseline centres the line's TALLEST run cell in the box, background rect
       behind each run, underline / lineThrough rects from run metrics. Paragraph-
       level italic / decoration fold in via lineColorRuns's base flags. */
    private fun drawLineStyled(
        inCanvas: Canvas,
        inLine: String,
        inLineStart: Int,
        inSpans: List<Range<SpanStyle>>?,
        inColor: ComposeColor,
        inFontSize: Int,
        inFontFamily: String?,
        inFontVariations: List<ComposeFontVariation.Setting>?,
        inBoxLeft: Float,
        inBoxTop: Float,
        inBoxWidth: Int,
        inBoxHeight: Float,
        inAlign: TextAlign,
        inBaseFont: Font,
        inBaseCapHeight: Float,
        inBaseItalic: Boolean,
        inBaseUnderline: Boolean,
        inBaseLineThrough: Boolean,
    ) {
        // Build style runs. inSpans is null when only paragraph-level base flags
        // affect painting — a single run covers the whole line with the base flags
        // folded in via lineColorRuns's inBase* args.
        val vRuns: List<ColorRun> = lineColorRuns(
            inLine, inLineStart, inSpans ?: emptyList(), inColor,
            inBaseItalic = inBaseItalic,
            inBaseUnderline = inBaseUnderline,
            inBaseLineThrough = inBaseLineThrough,
        )
        if (vRuns.isEmpty()) return

        // Per-run resolutions: run pixel size, run font (weight/variations), run
        // advance measured at ITS style. Fresh instances retained across the
        // background/paint/underline passes so we don't measure or resolve the
        // same run multiple times.
        val vN = vRuns.size
        val vRunFonts = arrayOfNulls<Font>(vN)
        val vRunPx = IntArray(vN)
        val vRunAdvances = FloatArray(vN)
        for (i in 0 until vN) {
            val vRun = vRuns[i]
            val vPx = resolveRunPx(vRun, inFontSize, fDensity)
            vRunPx[i] = vPx
            val vVars = runVariations(vRun, inFontVariations)
            val vRunFont = if (vRun.weight == 400 && vPx == inFontSize && vVars === inFontVariations) inBaseFont
                else {
                    val vRunKey = TypefaceKey(inFontFamily, variationsKey(vVars))
                    getFont(vRunKey, inFontFamily, vVars, vPx)
                }
            vRunFonts[i] = vRunFont
            vRunAdvances[i] = estimateTextWidth(
                inLine.substring(vRun.start, vRun.end),
                vPx, inFontFamily, vVars,
            ).toFloat()
        }

        // Alignment width = sum of styled advances (a bold/resized run pushes
        // the total right by its own amount).
        var vLineW = 0f
        for (i in 0 until vN) vLineW += vRunAdvances[i]
        val vPenX0 = alignX(inBoxLeft, inBoxWidth, vLineW, inAlign)

        // Common baseline: centre the line's TALLEST run cell in the box, then
        // sit every run's baseline on it. Uses -metrics.ascent (Skia ascent is
        // negative) for the run's cap-top-to-baseline distance. Without this a
        // bigger/smaller run centres its own texture and floats off the baseline.
        var vMaxCellH = inBaseFont.metrics.let { (it.descent - it.ascent).coerceAtLeast(1f) }
        var vMaxAscent = -inBaseFont.metrics.ascent
        for (i in 0 until vN) {
            val vFontI = vRunFonts[i] ?: continue
            val vM = vFontI.metrics
            val vCell = (vM.descent - vM.ascent).coerceAtLeast(1f)
            if (vCell > vMaxCellH) vMaxCellH = vCell
            val vAsc = -vM.ascent
            if (vAsc > vMaxAscent) vMaxAscent = vAsc
        }
        val vBaselineY = inBoxTop + (inBoxHeight - vMaxCellH) / 2f + vMaxAscent

        // 1) Backgrounds first — fill each run's slice of the line band before
        // the glyphs so they sit ON the background.
        var vRunX = vPenX0
        for (i in 0 until vN) {
            val vRun = vRuns[i]
            val vAdv = vRunAdvances[i]
            if (vRun.background != ComposeColorAlias.Unspecified && vRun.background.alpha > 0f) {
                val vBgPaint = Paint().apply { color = toSkiaColor(vRun.background); isAntiAlias = false }
                inCanvas.drawRect(
                    SkRect.makeXYWH(vRunX, inBoxTop, vAdv, inBoxHeight),
                    vBgPaint,
                )
                vBgPaint.close()
            }
            vRunX += vAdv
        }

        // 2) Glyphs — walk runs L→R, advancing by each run's styled advance so
        // a bold/resized run pushes the following runs over by the right amount.
        vRunX = vPenX0
        for (i in 0 until vN) {
            val vRun = vRuns[i]
            val vFont = vRunFonts[i] ?: continue
            val vAdv = vRunAdvances[i]
            val vSeg = expandTabs(inLine.substring(vRun.start, vRun.end))
            val vSegPaint = Paint().apply { color = toSkiaColor(vRun.color); isAntiAlias = true }
            // Faux italic via skewX. Reset after so the cached Font handle
            // is reusable by non-italic runs on the next line.
            val vHadSkew = vFont.skewX
            if (vRun.italic) vFont.skewX = -0.2f
            inCanvas.drawString(vSeg, vRunX, vBaselineY, vFont, vSegPaint)
            if (vRun.italic) vFont.skewX = vHadSkew
            vSegPaint.close()
            vRunX += vAdv
        }

        // 3) Underline / strikethrough — draw 1-2px rects from run metrics AFTER
        // the glyphs (so a heavier run's stems don't paint over the line). Skia
        // reports underlinePosition as distance from baseline to TOP of the
        // stroke (positive downward); strikeoutPosition is negative (above the
        // baseline, near x-height).
        vRunX = vPenX0
        for (i in 0 until vN) {
            val vRun = vRuns[i]
            val vFont = vRunFonts[i] ?: continue
            val vAdv = vRunAdvances[i]
            if (vRun.underline || vRun.lineThrough) {
                val vM = vFont.metrics
                val vDecoPaint = Paint().apply { color = toSkiaColor(vRun.color); isAntiAlias = false }
                if (vRun.underline) {
                    val vThick = (vM.underlineThickness ?: (vRunPx[i] / 16f)).coerceAtLeast(1f)
                    val vPos = vM.underlinePosition ?: (vRunPx[i] * 0.08f)
                    inCanvas.drawRect(
                        SkRect.makeXYWH(vRunX, vBaselineY + vPos, vAdv, vThick),
                        vDecoPaint,
                    )
                }
                if (vRun.lineThrough) {
                    val vThick = (vM.strikeoutThickness ?: (vRunPx[i] / 16f)).coerceAtLeast(1f)
                    val vPos = vM.strikeoutPosition ?: (-vRunPx[i] * 0.28f)
                    inCanvas.drawRect(
                        SkRect.makeXYWH(vRunX, vBaselineY + vPos, vAdv, vThick),
                        vDecoPaint,
                    )
                }
                vDecoPaint.close()
            }
            vRunX += vAdv
        }
    }

    /* Greedy soft-wrap that also tracks each line's start index in the
       ORIGINAL text. Hard lines (split on '\n') always start a new line,
       and the '\n' is consumed between them. Long hard lines are split at
       whitespace (the trailing whitespace stays attached to the preceding
       word so original-text length is preserved per hard line). Impossibly
       long words split mid-word. maxWidth >= half Int.MAX_VALUE = no wrap. */
    private fun wrapTextWithStarts(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String? = null, inFontVariations: List<ComposeFontVariation.Setting>? = null): WrappedText {
        if (inText.isEmpty()) return WrappedText(listOf(""), intArrayOf(0))
        val vLines = mutableListOf<String>()
        val vStarts = mutableListOf<Int>()
        val vUnbounded = inMaxWidth >= Int.MAX_VALUE / 2

        var vHardStart = 0
        while (vHardStart <= inText.length) {
            val vNl = inText.indexOf('\n', vHardStart)
            val vHardEnd = if (vNl < 0) inText.length else vNl
            val vHard = inText.substring(vHardStart, vHardEnd)

            if (vUnbounded || vHard.isEmpty() || estimateTextWidth(vHard, inFontSize, inFontFamily, inFontVariations) <= inMaxWidth) {
                vLines.add(vHard)
                vStarts.add(vHardStart)
            } else {
                wrapHardLine(vHard, inFontSize, inMaxWidth, vHardStart, vLines, vStarts, inFontFamily, inFontVariations)
            }

            if (vNl < 0) break
            vHardStart = vNl + 1 // skip the consumed '\n'
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
        inFontVariations: List<ComposeFontVariation.Setting>? = null,
    ) {
        var vCurrent = StringBuilder()
        var vLineStartInHard = 0 // start of the current sub-line within inLine
        var i = 0
        while (i < inLine.length) {
            val vWordStart = i
            while (i < inLine.length && !inLine[i].isWhitespace()) i++
            while (i < inLine.length && inLine[i].isWhitespace()) i++
            val vWord = inLine.substring(vWordStart, i)
            val vCandidate = vCurrent.toString() + vWord
            if (estimateTextWidth(vCandidate, inFontSize, inFontFamily, inFontVariations) <= inMaxWidth) {
                vCurrent.append(vWord)
            } else {
                if (vCurrent.isNotEmpty()) {
                    outLines.add(vCurrent.toString())
                    outStarts.add(inBaseOffset + vLineStartInHard)
                    vLineStartInHard += vCurrent.length
                    vCurrent = StringBuilder()
                }
                if (estimateTextWidth(vWord, inFontSize, inFontFamily, inFontVariations) > inMaxWidth) {
                    val vSub = StringBuilder()
                    for (ch in vWord) {
                        if (estimateTextWidth(vSub.toString() + ch, inFontSize, inFontFamily, inFontVariations) > inMaxWidth) {
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

    /* Width = sum of per-glyph advances. We don't use Font.measureTextWidth
       because in Skiko 0.148.2 it returns roughly half the real advance
       (measureText also undermeasures: rect.width ≈ 30 vs sum=62 for
       "Disabled" at 16px Roboto). getStringGlyphs + getWidths bypasses
       that path and matches what drawString actually paints, so the
       resulting layout no longer clips text inside Button / centered
       containers. Cached per (text, fontSize) since the wrap algorithm
       queries the same prefixes many times. */
    private fun estimateTextWidth(inText: String, inFontSize: Int, inFontFamily: String? = null, inFontVariations: List<ComposeFontVariation.Setting>? = null): Int {
        if (inText.isEmpty()) return 0
        val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
        // Key tab-containing text by the current tab width so changing "tab
        // size" re-measures instead of returning a stale cached width.
        val vCacheText = if ('\t' in inText) "${TextLayoutConfig.tabWidth} $inText" else inText
        val vCacheKey = Triple(vKey, vCacheText, inFontSize)
        fWidthCache[vCacheKey]?.let { return it }
        val vFont = getFont(vKey, inFontFamily, inFontVariations, inFontSize)
        val vGlyphs = vFont.getStringGlyphs(expandTabs(inText))
        val vAdvances = vFont.getWidths(vGlyphs)
        // Round up so the layout box never falls short of the drawn glyphs;
        // a 0.5px undershoot still clips antialiased pixels on the right edge.
        val vWidth = kotlin.math.ceil(vAdvances.sum()).toInt().coerceAtLeast(0)
        if (fWidthCache.size >= kWidthCacheMax) fWidthCache.clear()
        fWidthCache[vCacheKey] = vWidth
        return vWidth
    }

    fun destroy() {
        fFontCache.values.forEach { it.close() }
        fFontCache.clear()
        // Each Typeface (makeFromData / makeFromFile / makeClone) is reference-
        // counted by Skia — close() decrements. fFontMgr.default is an
        // unmanaged singleton; don't close.
        fTypefaceCache.values.forEach { it?.close() }
        fTypefaceCache.clear()
    }

    private fun getFont(inKey: TypefaceKey, inFamily: String?, inVariations: List<ComposeFontVariation.Setting>?, inSize: Int): Font {
        fFontCache[inKey to inSize]?.let { return it }
        val vTypeface = resolveTypeface(inKey, inFamily, inVariations) ?: fTypeface
        val vFont = Font(vTypeface, inSize.toFloat())
        fFontCache[inKey to inSize] = vFont
        return vFont
    }

    /* Looks up a registered IconFont typeface by family name and applies any
       variable-font axes via Typeface.makeClone. Caches the result (including
       failure → null) so we don't re-open or re-clone per frame. */
    private fun resolveTypeface(inKey: TypefaceKey, inFamily: String?, inVariations: List<ComposeFontVariation.Setting>?): Typeface? {
        fTypefaceCache[inKey]?.let { return it }
        if (fTypefaceCache.containsKey(inKey)) return null  // cached miss

        // Base typeface (no variations) for this family.
        val vBaseKey = TypefaceKey(inFamily, "")
        val vBase: Typeface? = fTypefaceCache[vBaseKey] ?: run {
            if (inFamily == null) {
                fTypefaceCache[vBaseKey] = fTypeface
                fTypeface
            } else {
                val vBytes = IconFont.bytesFor(inFamily)
                val vTf = if (vBytes != null) fFontMgr.makeFromData(Data.makeFromBytes(vBytes), 0) else null
                if (vTf == null) println("SkiaTextRenderer: IconFont '$inFamily' not registered, falling back to default")
                fTypefaceCache[vBaseKey] = vTf
                vTf
            }
        }

        // No variations requested → reuse the base.
        if (inVariations.isNullOrEmpty()) {
            fTypefaceCache[inKey] = vBase
            return vBase
        }
        if (vBase == null) {
            fTypefaceCache[inKey] = null
            return null
        }
        // Clone with the requested axes. makeClone returns a new ref-counted
        // Typeface; we own it until destroy() closes the cache.
        val vSkiaVars: Array<SkiaFontVariation> = inVariations.map {
            SkiaFontVariation(it.axisName, it.toVariationValue(null))
        }.toTypedArray()
        val vCloned = runCatching { vBase.makeClone(vSkiaVars) }.getOrNull()
            ?: vBase  // fallback to base if the font has no variable axes
        fTypefaceCache[inKey] = vCloned
        return vCloned
    }

    // ==================
    // MARK: Typeface resolution
    // ==================

    private fun pickTypeface(): Typeface? {
        // First: bundled font shipped next to the executable. This is the
        // only path that's known not to crash on macOS Skiko 0.144.6.
        bundledTypeface()?.let { return it }

        // Fallback: a system font we can hand to makeFromFile (still file-
        // backed, still avoids the matchFamilyStyle crash path).
        val vSystemPaths = listOf(
            "/System/Library/Fonts/Helvetica.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf"
        )
        for (path in vSystemPaths) {
            val vTf = fFontMgr.makeFromFile(path, 0)
            if (vTf != null) {
                println("SkiaTextRenderer: bundled font missing, falling back to $path")
                return vTf
            }
        }

        // Last resort: matchFamilyStyle. WILL crash measureText on macOS —
        // included only so the app surfaces a clear error rather than failing
        // silently when nothing else is available.
        println("SkiaTextRenderer: no usable font file found; falling back to matchFamilyStyle (this may crash on macOS).")
        return fFontMgr.matchFamilyStyle("Helvetica", FontStyle.NORMAL)
            ?: fFontMgr.matchFamiliesStyle(arrayOf<String?>(null), FontStyle.NORMAL)
    }

    private fun bundledTypeface(): Typeface? {
        val vBytes = loadComposeResourceBytes("font/NotoSans.ttf") ?: return null
        val vTf = fFontMgr.makeFromData(Data.makeFromBytes(vBytes), 0) ?: return null
        println("SkiaTextRenderer: loaded bundled font from data.kres (font/NotoSans.ttf)")
        return vTf
    }
}

internal fun toSkiaColor(inC: ComposeColor): Int =
    Color.makeARGB(inC.a8, inC.r8, inC.g8, inC.b8)

// Tab stops are rendered as a fixed run of spaces so a literal '\t' (e.g. in
// the API body editor) shows as indentation instead of a font-dependent,
// often zero-width glyph. Expansion is width-only / draw-only — callers keep
// the original '\t' in the text, so cursor / selection indices stay correct.
private fun expandTabs(inText: String): String =
    if ('\t' in inText) inText.replace("\t", " ".repeat(TextLayoutConfig.tabWidth)) else inText
