package com.compose.sdl.text

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

// ==================
// MARK: Native text drawing bridge (B5)
// ==================

/*
 Phase 9 B5 — bridges commonMain text drawing to the platform renderer without a
 hard dependency on it. A renderer's `Canvas` (Sdl3Canvas / future SkiaCanvas)
 implements [NativeTextCanvas]; the text [DrawModifierNode] gets the live Canvas via
 `drawIntoCanvas` and, if it's a NativeTextCanvas, asks it to paint the glyphs. Layout
 sizing still goes through `currentTextMeasurer` so bounds match the drawn text.

 This keeps text as a real upstream DrawModifierNode in the modifier chain — it draws
 through the coordinator pipeline like any other draw node.
*/
interface NativeTextCanvas {
	fun drawNativeText(
		inText: String,
		inSpans: List<Range<SpanStyle>>?,
		inX: Float,
		inY: Float,
		inBoxWidth: Float,
		inBoxHeight: Float,
		inColor: Color,
		inFontSizePx: Int,
		inTextAlign: TextAlign,
		inSoftWrap: Boolean,
		inFontFamily: String?,
		inFontVariations: List<FontVariation.Setting>?,
		// Paragraph-level TextStyle.fontStyle == Italic + merged textDecoration
		// (underline / line-through). Per-run overrides ride in via inSpans.
		inBaseItalic: Boolean = false,
		inTextDecoration: TextDecoration? = null,
	)
}

// Modifier element carrying the text paint params onto a LayoutNode's chain.
// Public (not internal) so :foundation's IconText.kt can reach it — same
// package, different module.
data class TextDrawElement(
	val text: String,
	val spans: List<Range<SpanStyle>>?,
	val color: Color,
	val fontSizePx: Int,
	val textAlign: TextAlign,
	val softWrap: Boolean,
	val fontFamily: String?,
	val fontVariations: List<FontVariation.Setting>?,
) : ModifierNodeElement<TextDrawNode>() {

	override fun create(): TextDrawNode =
		TextDrawNode(text, spans, color, fontSizePx, textAlign, softWrap, fontFamily, fontVariations)

	override fun update(node: TextDrawNode) {
		node.text = text
		node.spans = spans
		node.color = color
		node.fontSizePx = fontSizePx
		node.textAlign = textAlign
		node.softWrap = softWrap
		node.fontFamily = fontFamily
		node.fontVariations = fontVariations
	}
}

// DrawModifierNode that paints the text via the renderer Canvas at the node's origin.
class TextDrawNode(
	var text: String,
	var spans: List<Range<SpanStyle>>?,
	var color: Color,
	var fontSizePx: Int,
	var textAlign: TextAlign,
	var softWrap: Boolean,
	var fontFamily: String?,
	var fontVariations: List<FontVariation.Setting>?,
) : Modifier.Node(), DrawModifierNode {

	override fun ContentDrawScope.draw() {
		drawContent()
		drawIntoCanvas { vCanvas ->
			(vCanvas as? NativeTextCanvas)?.drawNativeText(
				text, spans, 0f, 0f, size.width, size.height,
				color, fontSizePx, textAlign, softWrap, fontFamily, fontVariations,
			)
		}
	}
}
