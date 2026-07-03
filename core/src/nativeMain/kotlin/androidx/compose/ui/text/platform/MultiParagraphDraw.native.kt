package androidx.compose.ui.text.platform

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.style.TextDecoration

// Phase 1 no-op: SdlParagraph.paint is stubbed, so MultiParagraph.paint (which routes here) doesn't
// rasterise yet. Text still renders through the project TextMeasurer/TextDrawNode path; this actual
// exists so the vendored MultiParagraph compiles. Phase 2 will draw glyphs onto the Canvas here.
internal actual fun MultiParagraph.drawMultiParagraph(
	canvas: Canvas,
	brush: Brush,
	alpha: Float,
	shadow: Shadow?,
	decoration: TextDecoration?,
	drawStyle: DrawStyle?,
	blendMode: BlendMode,
) {
}
