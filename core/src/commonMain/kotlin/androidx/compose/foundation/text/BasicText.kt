package androidx.compose.foundation.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.LocalSelectionRegistrar
import androidx.compose.foundation.text.selection.Selectable
import androidx.compose.foundation.text.selection.SelectionRect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import com.compose.desktop.native.layout.x
import com.compose.desktop.native.layout.y
import com.compose.desktop.native.node.ProjectLayoutNode
import androidx.compose.ui.node.MeasurePolicy
import com.compose.desktop.native.node.NodeApplier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import com.compose.desktop.native.text.WrappedText
import com.compose.desktop.native.text.currentTextMeasurer
import com.compose.desktop.native.text.currentViewportHeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

// ==================
// MARK: BasicText
// ==================

/* style: TextStyle matches upstream's signature; fontFamily / fontVariationSettings
   are documented non-upstream additions that the renderer needs for icon-font
   variable-axis support (upstream's FontFamily abstraction routes through
   FontFamily.Resolver, which we don't host — see CLAUDE.md). */
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation>? = null,
) {
    val (vColor, vSize, vAlign) = resolveTextStyle(style)
    BasicTextLayer(text, null, modifier, vColor, vSize, vAlign, softWrap, fontFamily, fontVariationSettings)
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
    fontVariationSettings: List<FontVariation>? = null,
) {
    val (vColor, vSize, vAlign) = resolveTextStyle(style)
    BasicTextLayer(text.text, text.spanStyles, modifier, vColor, vSize, vAlign, softWrap, fontFamily, fontVariationSettings)
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

// ==================
// MARK: Selection-aware layer
// ==================

/* Like official BasicText, the text is selection-aware: when a Selection
   registrar is in scope (i.e. inside a SelectionContainer) it registers a
   Selectable for cross-element drag selection and paints the highlight for its
   slice of the global selection — keeping full styling. With no registrar it's
   a plain leaf text node (the common, cheaper case). */
@Composable
private fun BasicTextLayer(
    text: String,
    spans: List<Range<SpanStyle>>?,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    textAlign: TextAlign,
    softWrap: Boolean,
    fontFamily: String?,
    fontVariationSettings: List<FontVariation>?,
    selectionColor: Color = Color(0x661E88E5L),
) {
    val vReg = LocalSelectionRegistrar.current
    if (vReg == null) {
        TextLeaf(text, spans, modifier, color, fontSize, textAlign, softWrap, fontFamily, fontVariationSettings)
        return
    }

    val vFontSize = fontSize.value.toInt()
    val vId = remember { vReg.nextId() }
    var vWinX by remember { mutableStateOf(0) }
    var vWinY by remember { mutableStateOf(0) }
    var vW by remember { mutableStateOf(0) }
    var vH by remember { mutableStateOf(0) }

    // Live holder the registrar queries during a drag. Updated each composition
    // so its geometry / text / font stay current; offsetAt re-wraps with the
    // latest width via the active measurer.
    val vSelectable = remember(vId) { TextSelectable(vId) }
    vSelectable.text = text
    vSelectable.fontSizePx = vFontSize
    vSelectable.family = fontFamily
    vSelectable.softWrap = softWrap
    vSelectable.windowX = vWinX
    vSelectable.windowY = vWinY
    vSelectable.width = vW
    vSelectable.height = vH

    DisposableEffect(vReg, vId) {
        vReg.register(vSelectable)
        onDispose { vReg.unregister(vId) }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { vWinX = it.x; vWinY = it.y }
            .onSizeChanged { vW = it.width; vH = it.height }
    ) {
        // Highlight this block's selected slice (behind the glyphs), one rect per
        // wrapped line so it hugs each line's text. Only the lines actually
        // on-screen are emitted — selecting a huge body would otherwise create
        // one node per selected line and stall layout (~10fps). The wrap is
        // remembered so scrolling with a live selection doesn't re-wrap the whole
        // body each frame.
        val vWrapW = if (softWrap && vW > 0) vW else Int.MAX_VALUE
        val vWrap = remember(text, vWrapW, vFontSize, fontFamily) {
            currentTextMeasurer.wrap(text, vFontSize, vWrapW, fontFamily)
        }
        val vLh = currentTextMeasurer.lineHeight(vFontSize, fontFamily).coerceAtLeast(1f)
        val vSel = vReg.rangeFor(vId)
        if (vSel != null) {
            val (vMinLine, vMinCol) = wrappedPosOf(vWrap, vSel.start)
            val (vMaxLine, vMaxCol) = wrappedPosOf(vWrap, vSel.end)
            // Cull to the visible line range (vWinY = this block's window-Y; the
            // viewport is [0, currentViewportHeight]). vWinY lags one frame, so
            // pad a whole screenful of "bleeding edge" each side — the rects for
            // lines scrolled into view this frame are then already there, no
            // pop-in even on a fast fling. Still ~3 screens of rects, not N.
            val vVpH = currentViewportHeight
            val vPad = if (vLh > 0f) (vVpH / vLh).toInt() + 4 else 4
            val vFrom = if (vVpH > 0) maxOf(vMinLine, ((-vWinY) / vLh).toInt() - vPad) else vMinLine
            val vTo = if (vVpH > 0) minOf(vMaxLine, ((vVpH - vWinY) / vLh).toInt() + vPad) else vMaxLine
            var vLine = vFrom
            while (vLine <= vTo) {
                val vLineStr = vWrap.lines.getOrElse(vLine) { "" }
                val vSc = if (vLine == vMinLine) vMinCol else 0
                val vEc = if (vLine == vMaxLine) vMaxCol else vLineStr.length
                val vSx = prefixW(vLineStr, vSc, vFontSize, fontFamily).toFloat()
                val vEx = prefixW(vLineStr, vEc, vFontSize, fontFamily).toFloat()
                SelectionRect(vSx, vLine * vLh, vEx - vSx, vLh, selectionColor)
                vLine++
            }
        }
        // Fill width only when wrapping (a paragraph) so highlight geometry spans
        // the line; inline/non-wrapping text must size to its content, or it
        // breaks layouts that place text in a Row (each run would fill the width).
        TextLeaf(text, spans, if (softWrap) Modifier.fillMaxWidth() else Modifier, color, fontSize, textAlign, softWrap, fontFamily, fontVariationSettings)
    }
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
    fontVariationSettings: List<FontVariation>?,
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

/* Defers to whatever TextMeasurer is currently installed so the laid-out
   bounds match the glyphs the renderer will draw. When softWrap is true,
   the measurer wraps lines to constraints.maxWidth; when false the text
   reports its natural width (potentially overflowing its container). */
internal val TextMeasurePolicy = MeasurePolicy { node, constraints ->
    val wrapWidth = if (node.softWrap && constraints.maxWidth != androidx.compose.ui.unit.Constraints.Infinity)
        constraints.maxWidth else Int.MAX_VALUE
    // Cache the wrap on the node so a huge static body isn't re-wrapped every
    // frame; the renderer reuses the same WrappedText for drawing.
    node.layoutText(wrapWidth)
    // Skip the per-line content-width scan when the width is already pinned
    // (fillMaxWidth / fixed) — coerceIn would discard it anyway.
    val w = if (constraints.minWidth >= constraints.maxWidth) constraints.maxWidth
            else node.textContentWidth().coerceIn(constraints.minWidth, constraints.maxWidth)
    val h = node.textMeasuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
    IntSize(w, h)
}

// ==================
// MARK: Selection support (drag offset mapping + highlight geometry)
// ==================

/* Mutable Selectable backing one selection-aware BasicText; the composable
   refreshes its fields each pass and the registrar reads them during a drag. */
private class TextSelectable(override val id: Long) : Selectable {
    override var text: String = ""
    override var windowX: Int = 0
    override var windowY: Int = 0
    override var width: Int = 0
    override var height: Int = 0
    var fontSizePx: Int = 16
    var family: String? = null
    var softWrap: Boolean = true

    override fun offsetAt(inLocalX: Int, inLocalY: Int): Int {
        val vWrapW = if (softWrap && width > 0) width else Int.MAX_VALUE
        val vWrap = currentTextMeasurer.wrap(text, fontSizePx, vWrapW, family)
        val vLh = currentTextMeasurer.lineHeight(fontSizePx, family).coerceAtLeast(1f)
        return offsetForPoint(vWrap, fontSizePx, family, inLocalX, inLocalY, vLh)
    }
}

private fun prefixW(inLine: String, inCol: Int, inFontSize: Int, inFamily: String?): Int {
    if (inCol <= 0 || inLine.isEmpty()) return 0
    val vEnd = inCol.coerceAtMost(inLine.length)
    return currentTextMeasurer.measure(inLine.substring(0, vEnd), inFontSize, inFontFamily = inFamily).width
}

/* Offset → (wrapped line, column). At a hard-'\n' boundary the cursor stays at
   the END of the preceding line. */
private fun wrappedPosOf(inWrap: WrappedText, inOffset: Int): Pair<Int, Int> {
    if (inWrap.lines.isEmpty()) return 0 to 0
    val vClamped = inOffset.coerceAtLeast(0)
    var lo = 0; var hi = inWrap.lines.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (inWrap.lineStarts[mid] <= vClamped) lo = mid else hi = mid - 1
    }
    val vCol = (vClamped - inWrap.lineStarts[lo]).coerceIn(0, inWrap.lines[lo].length)
    return lo to vCol
}

private fun charIndexAtX(inLine: String, inFontSize: Int, inFamily: String?, inX: Int): Int {
    if (inX <= 0 || inLine.isEmpty()) return 0
    var vPrev = 0
    for (i in inLine.indices) {
        val vNext = currentTextMeasurer.measure(inLine.substring(0, i + 1), inFontSize, inFontFamily = inFamily).width
        if (vNext > inX) return if ((inX - vPrev) < (vNext - inX)) i else i + 1
        vPrev = vNext
    }
    return inLine.length
}

private fun offsetForPoint(inWrap: WrappedText, inFontSize: Int, inFamily: String?, inX: Int, inY: Int, inLineHeight: Float): Int {
    if (inWrap.lines.isEmpty()) return 0
    val vLine = (inY / inLineHeight).toInt().coerceIn(0, inWrap.lines.size - 1)
    val vCol = charIndexAtX(inWrap.lines[vLine], inFontSize, inFamily, inX)
    return inWrap.lineStarts[vLine] + vCol
}
