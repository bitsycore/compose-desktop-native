package com.compose.desktop.native.renderer.skia

import com.compose.desktop.native.*

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.WrappedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface

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

    private val fFontMgr: FontMgr = FontMgr.default

    private val fTypeface: Typeface? = pickTypeface()

    private val fFontCache = mutableMapOf<Int, Font>()

    // Cache of measured widths per (text, fontSize). Even if Skia worked, this
    // would avoid re-measuring static labels each frame.
    private val fWidthCache = mutableMapOf<Pair<String, Int>, Int>()

    /* WORKAROUND for Skiko 0.144.6 / macOS — both Font.measureText and
       Font.measureTextWidth call into SkFont::measureText, which goes through
       SkTypeface_Mac::onCharsToGlyphs and aborts inside sk_malloc_flags. Same
       code path even for a file-loaded typeface, because SkFontMgr_Mac wraps
       the file data in a CoreText-backed SkTypeface_Mac. Until Skiko is fixed
       (or we get a FreeType-backed FontMgr) we estimate widths from the char
       class. font.metrics is safe to read — it doesn't trigger glyph lookup. */
    val textMeasurer: TextMeasurer = object : TextMeasurer {
        override fun measure(inText: String, inFontSize: Int, inMaxWidth: Int): IntSize {
            val vFont = getFont(inFontSize)
            val vMetrics = vFont.metrics
            val vLineHeight = (vMetrics.descent - vMetrics.ascent).toInt().coerceAtLeast(1)
            val vWrap = wrap(inText, inFontSize, inMaxWidth)
            val vWidth = if (vWrap.lines.isEmpty()) 0
                         else vWrap.lines.maxOf { estimateTextWidth(it, inFontSize) }
            return IntSize(vWidth, vLineHeight * vWrap.lines.size.coerceAtLeast(1))
        }

        override fun wrap(inText: String, inFontSize: Int, inMaxWidth: Int): WrappedText =
            wrapTextWithStarts(inText, inFontSize, inMaxWidth)

        override fun lineHeight(inFontSize: Int): Float {
            val vMetrics = getFont(inFontSize).metrics
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
        inAlign: TextAlign = TextAlign.Start
    ) {
        val vFont = getFont(inFontSize)
        val vPaint = Paint().apply {
            color = toSkiaColor(inColor)
            isAntiAlias = true
        }

        val vMetrics = vFont.metrics
        val vLineHeight = vMetrics.descent - vMetrics.ascent
        val vCapHeight = if (vMetrics.capHeight > 0f) vMetrics.capHeight
                         else inFontSize * 0.7f

        fun penXFor(inLineText: String): Float {
            val vLineWidth = estimateTextWidth(inLineText, inFontSize).toFloat()
            return when (inAlign) {
                TextAlign.Start  -> inX
                TextAlign.Center -> inX + (inBoxWidth - vLineWidth) / 2f
                TextAlign.End    -> inX + inBoxWidth.toFloat() - vLineWidth
            }
        }

        // Use the same wrap algorithm as measureText so layout and rendering
        // produce identical line breakdowns.
        val vLines = wrapTextWithStarts(inText, inFontSize, inBoxWidth).lines
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

            if (vUnbounded || vHard.isEmpty() || estimateTextWidth(vHard, inFontSize) <= inMaxWidth) {
                vLines.add(vHard)
                vStarts.add(vHardStart)
            } else {
                wrapHardLine(vHard, inFontSize, inMaxWidth, vHardStart, vLines, vStarts)
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
            if (estimateTextWidth(vCandidate, inFontSize) <= inMaxWidth) {
                vCurrent.append(vWord)
            } else {
                if (vCurrent.isNotEmpty()) {
                    outLines.add(vCurrent.toString())
                    outStarts.add(inBaseOffset + vLineStartInHard)
                    vLineStartInHard += vCurrent.length
                    vCurrent = StringBuilder()
                }
                if (estimateTextWidth(vWord, inFontSize) > inMaxWidth) {
                    val vSub = StringBuilder()
                    for (ch in vWord) {
                        if (estimateTextWidth(vSub.toString() + ch, inFontSize) > inMaxWidth) {
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
    private fun estimateTextWidth(inText: String, inFontSize: Int): Int {
        if (inText.isEmpty()) return 0
        fWidthCache[inText to inFontSize]?.let { return it }
        val vFont = getFont(inFontSize)
        val vGlyphs = vFont.getStringGlyphs(inText)
        val vAdvances = vFont.getWidths(vGlyphs)
        // Round up so the layout box never falls short of the drawn glyphs;
        // a 0.5px undershoot still clips antialiased pixels on the right edge.
        val vWidth = kotlin.math.ceil(vAdvances.sum()).toInt().coerceAtLeast(0)
        fWidthCache[inText to inFontSize] = vWidth
        return vWidth
    }

    fun destroy() {
        fFontCache.values.forEach { it.close() }
        fFontCache.clear()
        // fTypeface from makeFromFile is reference-counted by Skia — close()
        // decrements. fFontMgr.default is an unmanaged singleton; don't close.
        fTypeface?.close()
    }

    private fun getFont(inSize: Int): Font {
        fFontCache[inSize]?.let { return it }
        val vFont = Font(fTypeface, inSize.toFloat())
        fFontCache[inSize] = vFont
        return vFont
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
        val vPath = composeResourceFullPath("font/Roboto-Regular.ttf") ?: return null
        val vTf = fFontMgr.makeFromFile(vPath, 0) ?: return null
        println("SkiaTextRenderer: loaded bundled font from $vPath")
        return vTf
    }
}

internal fun toSkiaColor(inC: ComposeColor): Int =
    Color.makeARGB(inC.a8, inC.r8, inC.g8, inC.b8)
