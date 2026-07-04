package androidx.compose.foundation.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

// ==================
// MARK: BasicText
// ==================

/* style: TextStyle matches upstream's signature; fontFamily / fontVariationSettings
   are documented non-upstream additions that the renderer needs for icon-font
   variable-axis support (upstream's FontFamily abstraction routes through
   FontFamily.Resolver, which we don't host — see CLAUDE.md).

   Selection support has been retired from this leaf: it previously registered
   a Selectable with a project SelectionRegistrar so a wrapping
   SelectionContainer could paint highlights + copy text. That project engine
   has been removed pending vendoring the upstream selection engine; text
   currently is not selectable via SelectionContainer. Editable selection
   inside BasicTextField still works — the field owns its own highlight
   rendering (SelectionRect + wrapping math). */
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation.Setting>? = null,
) {
    val (vColor, vSize, vAlign) = resolveTextStyle(style)
    TextLeaf(text, null, modifier, vColor, vSize, vAlign, softWrap, fontFamily, fontVariationSettings)
}

/* AnnotatedString overload — draws `text.text` with per-span colours
   (text.spanStyles) in a single text node. Spans are color-only for layout:
   the plain text drives measurement/wrap, so this is safe to use as the
   display layer of an editable field (cursor / selection map to the plain
   text). Per-span weight / decoration aren't applied here — for those use the
   Material Text(AnnotatedString) overload, which lays out per-run. */
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation.Setting>? = null,
) {
    val (vColor, vSize, vAlign) = resolveTextStyle(style)
    TextLeaf(text.text, text.spanStyles, modifier, vColor, vSize, vAlign, softWrap, fontFamily, fontVariationSettings)
}

/* Resolve TextStyle to the (color, fontSize, textAlign) triple our text node
   accepts. Color.Unspecified → Color.White; TextUnit.Unspecified → 14.sp
   (matching upstream's defaults); null TextAlign → TextAlign.Start. */
private fun resolveTextStyle(inStyle: TextStyle): Triple<Color, TextUnit, TextAlign> {
    val vColor = if (inStyle.color == Color.Unspecified) Color.White else inStyle.color
    val vSize = if (inStyle.fontSize.isUnspecified) 14.sp else inStyle.fontSize
    val vAlign = inStyle.textAlign ?: TextAlign.Start
    return Triple(vColor, vSize, vAlign)
}

/* The text leaf node — defers measurement + drawing to the installed renderer. */
@Composable
private fun TextLeaf(
    text: String,
    spans: List<Range<SpanStyle>>?,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    textAlign: TextAlign,
    softWrap: Boolean,
    fontFamily: String?,
    fontVariationSettings: List<FontVariation.Setting>?,
) {
    // Phase 9 B5: build an upstream LayoutNode via the vendored Layout — sized by the
    // installed TextMeasurer, drawn by a TextDrawNode (DrawModifierNode) that bridges
    // to the renderer's native text drawing. Text is a real draw node in the chain.
    val vFontPx = fontSize.value.toInt()
    androidx.compose.ui.layout.Layout(
        modifier = modifier.then(
            com.compose.desktop.native.text.TextDrawElement(
                text = text,
                spans = spans,
                color = color,
                fontSizePx = vFontPx,
                textAlign = textAlign,
                softWrap = softWrap,
                fontFamily = fontFamily,
                fontVariations = fontVariationSettings,
            )
        ),
    ) { _, constraints ->
        val vWrapWidth =
            if (softWrap && constraints.maxWidth != androidx.compose.ui.unit.Constraints.Infinity) constraints.maxWidth
            else Int.MAX_VALUE
        val vSize = com.compose.desktop.native.text.currentTextMeasurer.measure(
            text, vFontPx, vWrapWidth, fontFamily, fontVariationSettings,
        )
        val w = if (constraints.minWidth >= constraints.maxWidth) constraints.maxWidth
                else vSize.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = vSize.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(w, h) {}
    }
}
