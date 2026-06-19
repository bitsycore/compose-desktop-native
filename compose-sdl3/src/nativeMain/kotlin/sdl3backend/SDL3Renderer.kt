package sdl3backend

import androidx.compose.ui.BackgroundModifier
import androidx.compose.ui.BorderModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: SDL3Renderer — Draws the LayoutNode tree
// ==================

class SDL3Renderer(private val backend: SDL3Backend) {
    private val textRenderer = SDL3TextRenderer(backend)

    val textMeasurer: TextMeasurer = TextMeasurer { inText, inFontSize ->
        val vSize = textRenderer.measureText(inText, inFontSize)
        IntSize(vSize.first, vSize.second)
    }

    fun init(): Boolean = textRenderer.init()
    fun destroy() = textRenderer.destroy()

    // ============
    //  Draw the full tree

    fun draw(root: LayoutNode) {
        drawNode(root)
    }

    private fun drawNode(node: LayoutNode) {
        val r = backend.renderer ?: return
        val ax = node.absoluteX
        val ay = node.absoluteY

        // ============
        //  Background modifier
        node.modifier.foldIn(Unit) { _, element ->
            if (element is BackgroundModifier) {
                setColor(r, element.color)
                fillRect(r, ax, ay, node.width, node.height)
            }
        }

        // ============
        //  Border modifier
        node.modifier.foldIn(Unit) { _, element ->
            if (element is BorderModifier) {
                setColor(r, element.color)
                val bw = element.width
                fillRect(r, ax, ay, node.width, bw)
                fillRect(r, ax, ay + node.height - bw, node.width, bw)
                fillRect(r, ax, ay + bw, bw, node.height - 2 * bw)
                fillRect(r, ax + node.width - bw, ay + bw, bw, node.height - 2 * bw)
            }
        }

        // ============
        //  Text leaf
        val text = node.text
        if (text != null && text.isNotEmpty()) {
            textRenderer.renderText(text, ax, ay, node.textColor, node.fontSize)
        }

        // ============
        //  Children
        for (child in node.children) {
            drawNode(child)
        }
    }

    // ============
    //  Helpers

    private fun setColor(r: COpaquePointer, c: Color) {
        SDL_SetRenderDrawColor(r.reinterpret(), c.r8.toUByte(), c.g8.toUByte(), c.b8.toUByte(), c.a8.toUByte())
    }

    private fun fillRect(r: COpaquePointer, x: Int, y: Int, w: Int, h: Int) {
        memScoped {
            val rect = alloc<SDL_FRect>()
            rect.x = x.toFloat()
            rect.y = y.toFloat()
            rect.w = w.toFloat()
            rect.h = h.toFloat()
            SDL_RenderFillRect(r.reinterpret(), rect.ptr)
        }
    }
}
