package androidx.compose.ui.text.platform

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.style.TextDecoration

// ==================
// MARK: MultiParagraph.drawMultiParagraph — Phase 2 SDL bridge
// ==================

/*
 Iterates the MultiParagraph's paragraphs, translating the canvas by each paragraph's height
 the same way the color-based `MultiParagraph.paint(canvas, color, ...)` does upstream, and
 routes each `Paragraph.paint(canvas, brush, ...)` call to our SdlParagraph.paint (which in
 turn casts the Canvas to NativeTextCanvas + calls drawNativeText).

 We use reflection-free access via the vendored MultiParagraph internal `paragraphInfoList`
 API — it's `internal` inside the same module, so we can walk it directly.
*/
internal actual fun MultiParagraph.drawMultiParagraph(
	canvas: Canvas,
	brush: Brush,
	alpha: Float,
	shadow: Shadow?,
	decoration: TextDecoration?,
	drawStyle: DrawStyle?,
	blendMode: BlendMode,
) {
	canvas.save()
	for (vInfo in paragraphInfoList) {
		vInfo.paragraph.paint(canvas, brush, alpha, shadow, decoration, drawStyle, blendMode)
		canvas.translate(0f, vInfo.paragraph.height)
	}
	canvas.restore()
}
