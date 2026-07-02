package com.compose.desktop.native.graphics

import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope

// ==================
// MARK: WrappedContentDrawScope
// ==================

/**
 * `ContentDrawScope` wrapper used by the renderer's Phase-8 chain-walk
 * draw pipeline. Wraps a renderer-specific [DrawScope] (Skia / SDL3) and
 * forwards every drawing call to it via Kotlin's `by` delegation; the
 * only new method is [drawContent], which invokes [inOnDrawContent] —
 * "draw the next inner thing in the chain" (typically the next inner
 * `DrawModifierNode.draw()` wrap, finally the LayoutNode's leaf body
 * + children).
 *
 * Build the chain outermost-to-innermost in the renderer:
 *
 *     var inner: () -> Unit = { drawNodeBody(node, scope) }
 *     for (i in drawNodes.indices.reversed()) {
 *         val node = drawNodes[i]
 *         val prev = inner
 *         inner = {
 *             val wrap = WrappedContentDrawScope(baseScope, prev)
 *             with(node) { wrap.draw() }
 *         }
 *     }
 *     inner()
 */
class WrappedContentDrawScope(
	inDelegate: DrawScope,
	private val inOnDrawContent: () -> Unit,
) : ContentDrawScope, DrawScope by inDelegate {
	override fun drawContent() { inOnDrawContent() }
}
