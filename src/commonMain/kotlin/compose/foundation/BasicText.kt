package compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import compose.ui.*
import compose.ui.node.LayoutNode
import compose.ui.node.MeasurePolicy
import compose.ui.node.NodeApplier

// ==================
// MARK: BasicText
// ==================

/** Leaf composable that measures and displays a text string. */
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: Int = 16
) {
    ComposeNode<LayoutNode, NodeApplier>(
        factory = { LayoutNode() },
        update = {
            set(text) { this.text = it }
            set(color) { this.textColor = it }
            set(fontSize) { this.fontSize = it }
            set(modifier) { this.modifier = it }
            set(Unit) {
                this.measurePolicy = TextMeasurePolicy
            }
        }
    )
}

/**
 * Measures text using an approximate character metric.
 * The real SDL3TextRenderer will override the rendered size at draw time,
 * but layout needs a reasonable estimate. We assume ~0.6 * fontSize per char width.
 */
internal val TextMeasurePolicy = MeasurePolicy { node, constraints ->
    val t = node.text ?: ""
    val fs = node.fontSize
    val charW = (fs * 0.6f).toInt().coerceAtLeast(1)
    val estW = charW * t.length
    val estH = (fs * 1.3f).toInt()

    val w = estW.coerceIn(constraints.minWidth, constraints.maxWidth)
    val h = estH.coerceIn(constraints.minHeight, constraints.maxHeight)
    IntSize(w, h)
}
