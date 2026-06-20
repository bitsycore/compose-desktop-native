package sdl3backend

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.toKString
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface
import sdl3.SDL_GetBasePath

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
    val textMeasurer: TextMeasurer = TextMeasurer { inText, inFontSize ->
        val vFont = getFont(inFontSize)
        val vMetrics = vFont.metrics
        val vLineHeight = (vMetrics.descent - vMetrics.ascent).toInt().coerceAtLeast(1)
        if ('\n' !in inText) {
            IntSize(estimateTextWidth(inText, inFontSize), vLineHeight)
        } else {
            val vLines = inText.split('\n')
            val vWidth = vLines.maxOf { estimateTextWidth(it, inFontSize) }
            IntSize(vWidth, vLineHeight * vLines.size)
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

        if ('\n' !in inText) {
            // Single line: cap-centred over the full box (button text in a
            // taller container lands at button centre).
            val vBaseline = inY + (inBoxHeight + vCapHeight) / 2f
            inCanvas.drawString(inText, penXFor(inText), vBaseline, vFont, vPaint)
        } else {
            // Multi-line: stack each line in its own lineHeight slot, cap-
            // centred within that slot.
            val vLines = inText.split('\n')
            for ((vIdx, vLine) in vLines.withIndex()) {
                val vSlotTop = inY + vIdx * vLineHeight
                val vBaseline = vSlotTop + (vLineHeight + vCapHeight) / 2f
                inCanvas.drawString(vLine, penXFor(vLine), vBaseline, vFont, vPaint)
            }
        }

        vPaint.close()
    }

    private fun estimateTextWidth(inText: String, inFontSize: Int): Int {
        fWidthCache[inText to inFontSize]?.let { return it }
        var vTotal = 0f
        for (c in inText) vTotal += charAdvance(c, inFontSize)
        val vResult = vTotal.toInt().coerceAtLeast(0)
        fWidthCache[inText to inFontSize] = vResult
        return vResult
    }

    /* Per-character advance estimate in pixels, ratios tuned against
       Roboto-Regular's actual UPM advances (units-per-em = 2048). Not
       perfect, but stable across frames and crash-free. */
    private fun charAdvance(inC: Char, inFontSize: Int): Float {
        val vBase = inFontSize.toFloat()
        val vRatio = when {
            inC == ' '              -> 0.27f
            inC == '-'              -> 0.29f
            inC == '+' || inC == '=' -> 0.58f
            inC == '(' || inC == ')' || inC == '[' || inC == ']' -> 0.30f
            inC == '.' || inC == ',' || inC == ':' || inC == ';' || inC == '\'' || inC == '!' -> 0.24f
            inC == 'i' || inC == 'l' || inC == 'I' || inC == '|' || inC == 'j' || inC == 't' -> 0.28f
            inC == 'm' || inC == 'M' || inC == 'W' || inC == 'w' -> 0.82f
            inC.isDigit()           -> 0.55f
            inC.isUpperCase()       -> 0.62f
            inC.isLowerCase()       -> 0.51f
            else                    -> 0.55f
        }
        return vBase * vRatio
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
        val vBaseRaw = SDL_GetBasePath() ?: return null
        val vBase = vBaseRaw.toKString()
        if (vBase.isEmpty()) return null
        val vPath = vBase + "fonts/Roboto-Regular.ttf"
        val vTf = fFontMgr.makeFromFile(vPath, 0) ?: return null
        println("SkiaTextRenderer: loaded bundled font from $vPath")
        return vTf
    }
}

internal fun toSkiaColor(inC: ComposeColor): Int =
    Color.makeARGB(inC.a8, inC.r8, inC.g8, inC.b8)
