package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.FontVariation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.sp

// ==================
// MARK: Text (Material)
// ==================

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onBackground,
    fontSize: Sp = 16.sp,
    textAlign: TextAlign = TextAlign.Start,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation>? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        textAlign = textAlign,
        softWrap = softWrap,
        fontFamily = fontFamily,
        fontVariationSettings = fontVariationSettings,
    )
}

// ==================
// MARK: Text(AnnotatedString)
// ==================

/* Renders an AnnotatedString by splitting it at every span boundary and
   composing each contiguous-style run as its own BasicText, arranged
   horizontally. Each run picks up the SpanStyle that covers it (last
   span wins on overlap), falling back to the default `color` /
   `fontSize` for unstyled regions.

   Limitation: this is single-line rendering — there's no per-glyph
   layout pass that can break a run across lines. For multi-line styled
   text, use explicit '\n' between Text(AnnotatedString) calls or stick
   to the plain Text(String) overload.

   Decoration (underline / line-through) is drawn via drawBehind under
   each run; background tint is applied via Modifier.background. */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onBackground,
    fontSize: Sp = 16.sp,
    textAlign: TextAlign = TextAlign.Start,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation>? = null,
) {
    val vRuns = splitIntoRuns(text)
    Row(modifier = modifier) {
        for (vRun in vRuns) {
            val vStyle = vRun.style
            val vColor = if (vStyle != null && vStyle.color != Color.Unspecified) vStyle.color else color
            val vSize = if (vStyle != null && vStyle.fontSize.value.isFinite()) vStyle.fontSize else fontSize
            val vFamily = if (vStyle?.fontFamily != null) familyName(vStyle.fontFamily) ?: fontFamily else fontFamily
            // Background tint: paint behind the run if a background colour is set.
            val vBgModifier = if (vStyle != null && vStyle.background != Color.Unspecified)
                Modifier.background(vStyle.background) else Modifier
            // Underline / line-through: drawBehind paints a 1px line under the run.
            val vDec: TextDecoration? = vStyle?.textDecoration
            val vDecorModifier = if (vDec != null && vDec != TextDecoration.None) {
                val vDecColor = vColor
                val vHasUnder = TextDecoration.Underline in vDec
                val vHasLine = TextDecoration.LineThrough in vDec
                Modifier.drawBehind {
                    if (vHasUnder) {
                        drawLine(
                            color = vDecColor,
                            start = Offset(0f, size.height - 2f),
                            end = Offset(size.width, size.height - 2f),
                            strokeWidth = 1f,
                        )
                    }
                    if (vHasLine) {
                        drawLine(
                            color = vDecColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1f,
                        )
                    }
                }
            } else Modifier
            BasicText(
                text = vRun.text,
                modifier = vBgModifier.then(vDecorModifier),
                color = vColor,
                fontSize = vSize,
                textAlign = textAlign,
                softWrap = false,  // Per-run wrap doesn't make sense; only the parent Row does.
                fontFamily = vFamily,
                fontVariationSettings = vStyle?.let { extractFontVariations(it, fontVariationSettings) } ?: fontVariationSettings,
            )
        }
    }
}

/* One contiguous-style segment of an AnnotatedString. */
private data class Run(val text: String, val style: SpanStyle?)

/* Split the AnnotatedString at every span boundary so each output run
   has at most one SpanStyle covering its full span. When multiple
   SpanStyles overlap, the LAST one (highest index) wins — matches the
   upstream "later annotations override earlier" rule. */
private fun splitIntoRuns(inText: AnnotatedString): List<Run> {
    if (inText.spanStyles.isEmpty() || inText.length == 0) {
        return listOf(Run(inText.text, null))
    }
    val vSet = mutableSetOf(0, inText.length)
    for (vR in inText.spanStyles) {
        vSet.add(vR.start.coerceIn(0, inText.length))
        vSet.add(vR.end.coerceIn(0, inText.length))
    }
    val vSorted = vSet.toList().sorted()
    val vOut = mutableListOf<Run>()
    for (vI in 0 until vSorted.size - 1) {
        val vS = vSorted[vI]
        val vE = vSorted[vI + 1]
        if (vS == vE) continue
        // Walk spans in order so the LAST matching one wins.
        var vActive: SpanStyle? = null
        for (vR in inText.spanStyles) {
            if (vR.start <= vS && vR.end >= vE) vActive = vR.item
        }
        vOut.add(Run(inText.text.substring(vS, vE), vActive))
    }
    return vOut
}

/* FontFamily → renderer family name. Today only Named falls through;
   the generic families resolve to the default font. */
private fun familyName(inFamily: androidx.compose.ui.text.FontFamily?): String? = when (inFamily) {
    is androidx.compose.ui.text.FontFamily.Named -> inFamily.name
    else                                          -> null
}

/* Translate FontWeight from a SpanStyle into the wght FontVariation
   the renderer already honours. Falls back to inFallback when no
   weight is set on the span. */
private fun extractFontVariations(
    inSpan: SpanStyle,
    inFallback: List<FontVariation>?,
): List<FontVariation>? {
    val vWeight = inSpan.fontWeight?.weight ?: return inFallback
    return listOf(FontVariation.Weight(vWeight))
}
