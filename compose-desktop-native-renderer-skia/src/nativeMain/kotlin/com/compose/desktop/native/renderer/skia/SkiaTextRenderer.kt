package com.compose.desktop.native.renderer.skia

import com.compose.desktop.native.*
import com.compose.desktop.native.icons.IconFont

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRendererCapabilities
import androidx.compose.ui.text.WrappedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface
import androidx.compose.ui.text.FontVariation as ComposeFontVariation
import org.jetbrains.skia.FontVariation as SkiaFontVariation

// ==================
// MARK: SkiaTextRenderer
// ==================

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

    private val fTypeface: Typeface? = pickTypeface()

    /* Typeface key bundles family with a stable string representation of any
       variable-font axis settings, so each variant gets its own makeClone'd
       typeface in the cache. Null variations / null family resolve to the
       default-font slot. */
    private data class TypefaceKey(val family: String?, val variations: String)

    private fun variationsKey(inV: List<ComposeFontVariation>?): String {
        if (inV.isNullOrEmpty()) return ""
        // Sort by tag so equivalent settings hash the same regardless of
        // caller-supplied order.
        return inV.sortedBy { it.axisTag }.joinToString(",") { "${it.axisTag}=${it.value}" }
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
        override fun measure(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<ComposeFontVariation>?): IntSize {
            val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
            val vFont = getFont(vKey, inFontFamily, inFontVariations, inFontSize)
            val vMetrics = vFont.metrics
            val vLineHeight = (vMetrics.descent - vMetrics.ascent).toInt().coerceAtLeast(1)
            val vWrap = wrap(inText, inFontSize, inMaxWidth, inFontFamily, inFontVariations)
            val vWidth = if (vWrap.lines.isEmpty()) 0
                         else vWrap.lines.maxOf { estimateTextWidth(it, inFontSize, inFontFamily, inFontVariations) }
            return IntSize(vWidth, vLineHeight * vWrap.lines.size.coerceAtLeast(1))
        }

        override fun wrap(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String?, inFontVariations: List<ComposeFontVariation>?): WrappedText =
            wrapTextWithStarts(inText, inFontSize, inMaxWidth, inFontFamily, inFontVariations)

        override fun lineHeight(inFontSize: Int, inFontFamily: String?, inFontVariations: List<ComposeFontVariation>?): Float {
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
        inFontVariations: List<ComposeFontVariation>? = null,
    ) {
        val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
        val vFont = getFont(vKey, inFontFamily, inFontVariations, inFontSize)
        val vPaint = Paint().apply {
            color = toSkiaColor(inColor)
            isAntiAlias = true
        }

        val vMetrics = vFont.metrics
        val vLineHeight = vMetrics.descent - vMetrics.ascent
        val vCapHeight = if (vMetrics.capHeight > 0f) vMetrics.capHeight
                         else inFontSize * 0.7f

        fun penXFor(inLineText: String): Float {
            val vLineWidth = estimateTextWidth(inLineText, inFontSize, inFontFamily, inFontVariations).toFloat()
            return when (inAlign) {
                TextAlign.Start  -> inX
                TextAlign.Center -> inX + (inBoxWidth - vLineWidth) / 2f
                TextAlign.End    -> inX + inBoxWidth.toFloat() - vLineWidth
            }
        }

        // Use the same wrap algorithm as measureText so layout and rendering
        // produce identical line breakdowns. softWrap = false (e.g. a
        // singleLine field) stays one line and overflows the box width.
        val vWrapWidth = if (inSoftWrap) inBoxWidth else Int.MAX_VALUE
        val vLines = wrapTextWithStarts(inText, inFontSize, vWrapWidth, inFontFamily, inFontVariations).lines
        if (vLines.size == 1 && '\n' !in inText) {
            // Single line, no wrap, no explicit newlines: cap-centre across
            // the full box (button text in a taller container).
            val vBaseline = inY + (inBoxHeight + vCapHeight) / 2f
            inCanvas.drawString(vLines[0], penXFor(vLines[0]), vBaseline, vFont, vPaint)
        } else {
            for ((vIdx, vLine) in vLines.withIndex()) {
                val vSlotTop = inY + vIdx * vLineHeight
                val vBaseline = vSlotTop + (vLineHeight + vCapHeight) / 2f
                inCanvas.drawString(vLine, penXFor(vLine), vBaseline, vFont, vPaint)
            }
        }

        vPaint.close()
    }

    /* Greedy soft-wrap that also tracks each line's start index in the
       ORIGINAL text. Hard lines (split on '\n') always start a new line,
       and the '\n' is consumed between them. Long hard lines are split at
       whitespace (the trailing whitespace stays attached to the preceding
       word so original-text length is preserved per hard line). Impossibly
       long words split mid-word. maxWidth >= half Int.MAX_VALUE = no wrap. */
    private fun wrapTextWithStarts(inText: String, inFontSize: Int, inMaxWidth: Int, inFontFamily: String? = null, inFontVariations: List<ComposeFontVariation>? = null): WrappedText {
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
        inFontVariations: List<ComposeFontVariation>? = null,
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
    private fun estimateTextWidth(inText: String, inFontSize: Int, inFontFamily: String? = null, inFontVariations: List<ComposeFontVariation>? = null): Int {
        if (inText.isEmpty()) return 0
        val vKey = TypefaceKey(inFontFamily, variationsKey(inFontVariations))
        val vCacheKey = Triple(vKey, inText, inFontSize)
        fWidthCache[vCacheKey]?.let { return it }
        val vFont = getFont(vKey, inFontFamily, inFontVariations, inFontSize)
        val vGlyphs = vFont.getStringGlyphs(inText)
        val vAdvances = vFont.getWidths(vGlyphs)
        // Round up so the layout box never falls short of the drawn glyphs;
        // a 0.5px undershoot still clips antialiased pixels on the right edge.
        val vWidth = kotlin.math.ceil(vAdvances.sum()).toInt().coerceAtLeast(0)
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

    private fun getFont(inKey: TypefaceKey, inFamily: String?, inVariations: List<ComposeFontVariation>?, inSize: Int): Font {
        fFontCache[inKey to inSize]?.let { return it }
        val vTypeface = resolveTypeface(inKey, inFamily, inVariations) ?: fTypeface
        val vFont = Font(vTypeface, inSize.toFloat())
        fFontCache[inKey to inSize] = vFont
        return vFont
    }

    /* Looks up a registered IconFont typeface by family name and applies any
       variable-font axes via Typeface.makeClone. Caches the result (including
       failure → null) so we don't re-open or re-clone per frame. */
    private fun resolveTypeface(inKey: TypefaceKey, inFamily: String?, inVariations: List<ComposeFontVariation>?): Typeface? {
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
            SkiaFontVariation(it.axisTag, it.value)
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
        val vBytes = loadComposeResourceBytes("font/Roboto-Regular.ttf") ?: return null
        val vTf = fFontMgr.makeFromData(Data.makeFromBytes(vBytes), 0) ?: return null
        println("SkiaTextRenderer: loaded bundled font from data.kres (font/Roboto-Regular.ttf)")
        return vTf
    }
}

internal fun toSkiaColor(inC: ComposeColor): Int =
    Color.makeARGB(inC.a8, inC.r8, inC.g8, inC.b8)
