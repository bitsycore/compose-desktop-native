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
    val vLines = splitIntoRunLines(text)
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        for (vLine in vLines) {
            androidx.compose.foundation.layout.Row {
                if (vLine.isEmpty()) {
                    // Empty line still needs to take the line-height — emit a
                    // single-space BasicText so the row doesn't collapse to 0px.
                    BasicText(text = " ", color = color, fontSize = fontSize, softWrap = false,
                        fontFamily = fontFamily, fontVariationSettings = fontVariationSettings)
                } else for (vRun in vLine) {
                    val vStyle = vRun.style
                    val vColor = if (vStyle != null && vStyle.color != Color.Unspecified) vStyle.color else color
                    val vSize = if (vStyle != null && vStyle.fontSize.value.isFinite()) vStyle.fontSize else fontSize
                    val vFamily = if (vStyle?.fontFamily != null) familyName(vStyle.fontFamily) ?: fontFamily else fontFamily
                    val vBgModifier = if (vStyle != null && vStyle.background != Color.Unspecified)
                        Modifier.background(vStyle.background) else Modifier
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
                        softWrap = false,
                        fontFamily = vFamily,
                        fontVariationSettings = vStyle?.let { extractFontVariations(it, fontVariationSettings) } ?: fontVariationSettings,
                    )
                }
            }
        }
    }
}

/* Split into lines of runs. First we cut the AnnotatedString at every
   '\n' (newlines aren't run-bearing — they end a Row), then within each
   line we re-split at span boundaries so each in-line run carries the
   right SpanStyle. Result is List<line> of List<run>. */
private fun splitIntoRunLines(inText: AnnotatedString): List<List<Run>> {
    if (inText.length == 0) return listOf(emptyList())
    val vOut = mutableListOf<List<Run>>()
    var vLineStart = 0
    for (vI in inText.text.indices) {
        if (inText.text[vI] == '\n') {
            vOut.add(slicedRuns(inText, vLineStart, vI))
            vLineStart = vI + 1
        }
    }
    vOut.add(slicedRuns(inText, vLineStart, inText.length))
    return vOut
}

/* Run list for inText[inStart, inEnd) — same algorithm as splitIntoRuns
   but limited to a sub-range and clipping span boundaries to that range. */
private fun slicedRuns(inText: AnnotatedString, inStart: Int, inEnd: Int): List<Run> {
    if (inStart >= inEnd) return emptyList()
    if (inText.spanStyles.isEmpty()) return listOf(Run(inText.text.substring(inStart, inEnd), null))
    val vSet = mutableSetOf(inStart, inEnd)
    for (vR in inText.spanStyles) {
        val vS = vR.start.coerceIn(inStart, inEnd)
        val vE = vR.end.coerceIn(inStart, inEnd)
        if (vS < vE) { vSet.add(vS); vSet.add(vE) }
    }
    val vSorted = vSet.toList().sorted()
    val vOut = mutableListOf<Run>()
    for (vI in 0 until vSorted.size - 1) {
        val vS = vSorted[vI]; val vE = vSorted[vI + 1]
        if (vS == vE) continue
        var vActive: SpanStyle? = null
        for (vR in inText.spanStyles) {
            if (vR.start <= vS && vR.end >= vE) vActive = vR.item
        }
        vOut.add(Run(inText.text.substring(vS, vE), vActive))
    }
    return vOut
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
