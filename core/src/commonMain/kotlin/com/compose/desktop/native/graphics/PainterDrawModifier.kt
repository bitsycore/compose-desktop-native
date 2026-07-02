package com.compose.desktop.native.graphics

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.ResourcePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.res.ResourceKind

// ==================
// MARK: Native painter (image) drawing bridge (B5)
// ==================

/*
 Phase 9 B5 — image drawing bridge, twin of NativeTextCanvas. A renderer Canvas
 (Sdl3Canvas / future SkiaCanvas) implements [NativePainterCanvas]; the image
 [DrawModifierNode] gets the live Canvas via `drawIntoCanvas` and asks it to paint
 the decoded resource (contentScale + alpha applied by the renderer's image cache).
*/
interface NativePainterCanvas {
	fun drawNativePainter(
		inResourcePath: String,
		inKind: ResourceKind,
		inX: Float,
		inY: Float,
		inWidth: Float,
		inHeight: Float,
		inContentScale: ContentScale,
		inAlpha: Float,
	)
}

internal data class PainterDrawElement(
	val painter: Painter,
	val contentScale: ContentScale,
	val alpha: Float,
) : ModifierNodeElement<PainterDrawNode>() {

	override fun create(): PainterDrawNode = PainterDrawNode(painter, contentScale, alpha)

	override fun update(node: PainterDrawNode) {
		node.painter = painter
		node.contentScale = contentScale
		node.alpha = alpha
	}
}

internal class PainterDrawNode(
	var painter: Painter,
	var contentScale: ContentScale,
	var alpha: Float,
) : Modifier.Node(), DrawModifierNode {

	override fun ContentDrawScope.draw() {
		drawContent()
		val vRes = painter as? ResourcePainter ?: return
		drawIntoCanvas { vCanvas ->
			(vCanvas as? NativePainterCanvas)?.drawNativePainter(
				vRes.resourcePath, vRes.kind, 0f, 0f, size.width, size.height, contentScale, alpha,
			)
		}
	}
}
