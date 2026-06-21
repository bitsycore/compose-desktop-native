package androidx.compose.foundation.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.MeasurePolicy
import androidx.compose.ui.node.NodeApplier
import androidx.compose.ui.text.FontVariation
import androidx.compose.ui.text.currentTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.sp

// ==================
// MARK: BasicText
// ==================

@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: Sp = 16.sp,
    textAlign: TextAlign = TextAlign.Start,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation>? = null,
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(text) { this.text = it }
            set(color) { this.textColor = it }
            set(fontSize) { this.fontSize = it.value.toInt() }
            set(textAlign) { this.textAlign = it }
            set(softWrap) { this.softWrap = it }
            set(fontFamily) { this.fontFamily = it }
            set(fontVariationSettings) { this.fontVariationSettings = it }
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = TextMeasurePolicy
            }
        }
    )
}

/* Defers to whatever TextMeasurer is currently installed so the laid-out
   bounds match the glyphs the renderer will draw. When softWrap is true,
   the measurer wraps lines to constraints.maxWidth; when false the text
   reports its natural width (potentially overflowing its container). */
internal val TextMeasurePolicy = MeasurePolicy { node, constraints ->
    val t = node.text ?: ""
    val wrapWidth = if (node.softWrap && constraints.maxWidth != androidx.compose.ui.unit.Constraints.Infinity)
        constraints.maxWidth else Int.MAX_VALUE
    val measured = currentTextMeasurer.measure(t, node.fontSize, wrapWidth, node.fontFamily, node.fontVariationSettings)

    val w = measured.width.coerceIn(constraints.minWidth, constraints.maxWidth)
    val h = measured.height.coerceIn(constraints.minHeight, constraints.maxHeight)
    IntSize(w, h)
}
