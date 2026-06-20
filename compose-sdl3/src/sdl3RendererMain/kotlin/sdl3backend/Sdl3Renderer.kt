package sdl3backend

import androidx.compose.ui.BackgroundModifier
import androidx.compose.ui.BorderModifier
import androidx.compose.ui.ClipModifier
import androidx.compose.ui.HorizontalScrollModifier
import androidx.compose.ui.VerticalScrollModifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.LayoutNode
import kotlinx.cinterop.*
import sdl3.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==================
// MARK: Sdl3Renderer
// ==================

/* Walks the LayoutNode tree and renders backgrounds, borders, text, and
   clipped children using only SDL3 primitives. Used by Sdl3RenderBackend
   on every target — primary renderer on mingwX64, comparison renderer
   on Skia targets.

   Limitations vs Skia: shape edges aren't antialiased, and rounded
   corners are approximated with fans of triangles (16 segments). */
internal class Sdl3Renderer(
    private val backend: SDL3Backend,
    private val textRenderer: Sdl3TextRenderer,
    private val imageCache: Sdl3ImageCache,
) {

    private val kClearR: UByte = 0x12u
    private val kClearG: UByte = 0x12u
    private val kClearB: UByte = 0x12u

    fun draw(inRoot: LayoutNode) {
        val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        SDL_SetRenderDrawColor(vRenderer, kClearR, kClearG, kClearB, 0xFFu)
        SDL_RenderClear(vRenderer)
        drawNode(inRoot)
    }

    private fun drawNode(inNode: LayoutNode) {
        val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        val vAx = inNode.absoluteX.toFloat()
        val vAy = inNode.absoluteY.toFloat()
        val vW = inNode.width.toFloat()
        val vH = inNode.height.toFloat()

        // ============
        //  Background
        inNode.modifier.foldIn(Unit) { _, element ->
            if (element is BackgroundModifier && element.color.alpha > 0f) {
                setColor(vRenderer, element.color)
                fillOutline(vRenderer, vAx, vAy, vW, vH, element.shape.outline(inNode.width, inNode.height))
            }
        }

        // ============
        //  Border (drawn as four thin filled rects centred on the edge)
        inNode.modifier.foldIn(Unit) { _, element ->
            if (element is BorderModifier && element.width > 0 && element.color.alpha > 0f) {
                setColor(vRenderer, element.color)
                strokeOutline(
                    vRenderer,
                    vAx, vAy, vW, vH,
                    element.shape.outline(inNode.width, inNode.height),
                    element.width.toFloat(),
                )
            }
        }

        // ============
        //  Text leaf
        val vText = inNode.text
        if (!vText.isNullOrEmpty()) {
            // Word-wrap to the box width so multi-line text renders the
            // same lines the layout pass measured against.
            val vLines = textRenderer.textMeasurer.wrap(vText, inNode.fontSize, inNode.width).lines
            val vLineHeight = textRenderer.textMeasurer.lineHeight(inNode.fontSize).toInt()
            if (vLines.size == 1 && '\n' !in vText) {
                textRenderer.drawText(
                    vLines[0],
                    inNode.absoluteX, inNode.absoluteY,
                    inNode.width, inNode.height,
                    inNode.textColor, inNode.fontSize, inNode.textAlign,
                )
            } else {
                for ((idx, line) in vLines.withIndex()) {
                    val vSlotTop = inNode.absoluteY + idx * vLineHeight
                    textRenderer.drawText(
                        line,
                        inNode.absoluteX, vSlotTop,
                        inNode.width, vLineHeight,
                        inNode.textColor, inNode.fontSize, inNode.textAlign,
                    )
                }
            }
        }

        // ============
        //  Image leaf (decoded + cached by Sdl3ImageCache, painted per
        //  contentScale / alpha into the node bounds)
        val vPainter = inNode.painter
        if (vPainter != null) {
            imageCache.draw(
                vPainter.resourcePath, vPainter.kind,
                vAx, vAy, vW, vH,
                inNode.contentScale, inNode.imageAlpha,
            )
        }

        // ============
        //  Children (clipped to shape if a clip modifier is present, or
        //  auto-clipped to bounds when scrolling — same rule as the Skia
        //  renderer)
        var vClipShape: Shape? = null
        inNode.modifier.foldIn(Unit) { _, element ->
            when {
                element is ClipModifier -> vClipShape = element.shape
                element is VerticalScrollModifier && vClipShape == null   -> vClipShape = RectangleShape
                element is HorizontalScrollModifier && vClipShape == null -> vClipShape = RectangleShape
            }
        }
        if (vClipShape != null && inNode.children.isNotEmpty()) {
            // SDL clip only supports rects — round corners are ignored here.
            memScoped {
                val vRect = alloc<SDL_Rect>()
                vRect.x = inNode.absoluteX
                vRect.y = inNode.absoluteY
                vRect.w = inNode.width
                vRect.h = inNode.height
                SDL_SetRenderClipRect(vRenderer, vRect.ptr)
                for (child in inNode.children) drawNode(child)
                SDL_SetRenderClipRect(vRenderer, null)
            }
        } else {
            for (child in inNode.children) drawNode(child)
        }
    }

    // ==================
    // MARK: Outline fill / stroke
    // ==================

    /* Active draw colour as 0..1 floats — used by SDL_RenderGeometry's
       per-vertex color. Kept in sync with setColor() so we don't have to
       round-trip through SDL_GetRenderDrawColor every call. */
    private var fR: Float = 0f
    private var fG: Float = 0f
    private var fB: Float = 0f
    private var fA: Float = 0f

    private fun fillOutline(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inX: Float,
        inY: Float,
        inW: Float,
        inH: Float,
        inOutline: Outline,
    ) {
        when (inOutline) {
            is Outline.Rectangle -> fillRect(inRenderer, inX, inY, inW, inH)
            is Outline.RoundedRect -> fillRoundedRect(inRenderer, inX, inY, inW, inH, inOutline.cornerRadius.toFloat())
        }
    }

    private fun strokeOutline(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inX: Float,
        inY: Float,
        inW: Float,
        inH: Float,
        inOutline: Outline,
        inStroke: Float,
    ) {
        val vR = when (inOutline) {
            is Outline.Rectangle -> 0f
            is Outline.RoundedRect -> inOutline.cornerRadius.toFloat()
        }.coerceAtMost(kotlin.math.min(inW, inH) / 2f)

        // Straight edges: top, bottom, left, right strips, inset by the
        // corner radius so they don't overlap the (separate) corner arcs.
        if (vR < inW) {
            fillRect(inRenderer, inX + vR, inY, inW - 2f * vR, inStroke)
            fillRect(inRenderer, inX + vR, inY + inH - inStroke, inW - 2f * vR, inStroke)
        }
        if (vR < inH) {
            fillRect(inRenderer, inX, inY + vR, inStroke, inH - 2f * vR)
            fillRect(inRenderer, inX + inW - inStroke, inY + vR, inStroke, inH - 2f * vR)
        }
        if (vR <= 0.5f) return

        // Rounded corners: a ring strip per quadrant. Triangle-strip
        // between an outer circle (radius vR) and an inner one (radius
        // vR - stroke), 12 segments per quadrant.
        val vInnerR = (vR - inStroke).coerceAtLeast(0f)
        strokeCornerArc(inRenderer, inX + vR,       inY + vR,       vR, vInnerR, PI.toFloat(),         1.5f * PI.toFloat())
        strokeCornerArc(inRenderer, inX + inW - vR, inY + vR,       vR, vInnerR, 1.5f * PI.toFloat(),  2f * PI.toFloat())
        strokeCornerArc(inRenderer, inX + inW - vR, inY + inH - vR, vR, vInnerR, 0f,                   0.5f * PI.toFloat())
        strokeCornerArc(inRenderer, inX + vR,       inY + inH - vR, vR, vInnerR, 0.5f * PI.toFloat(),  PI.toFloat())
    }

    private fun strokeCornerArc(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inCx: Float,
        inCy: Float,
        inOuterR: Float,
        inInnerR: Float,
        inStartRad: Float,
        inEndRad: Float,
    ) {
        val kSegments = 12
        val vVerts = kSegments * 6  // two triangles per segment
        memScoped {
            val vArr = allocArray<SDL_Vertex>(vVerts)
            for (i in 0 until kSegments) {
                val t0 = inStartRad + (inEndRad - inStartRad) * (i.toFloat() / kSegments)
                val t1 = inStartRad + (inEndRad - inStartRad) * ((i + 1).toFloat() / kSegments)
                val ox0 = inCx + inOuterR * cos(t0); val oy0 = inCy + inOuterR * sin(t0)
                val ox1 = inCx + inOuterR * cos(t1); val oy1 = inCy + inOuterR * sin(t1)
                val ix0 = inCx + inInnerR * cos(t0); val iy0 = inCy + inInnerR * sin(t0)
                val ix1 = inCx + inInnerR * cos(t1); val iy1 = inCy + inInnerR * sin(t1)
                val base = i * 6
                writeVertex(vArr[base + 0], ox0, oy0)
                writeVertex(vArr[base + 1], ix0, iy0)
                writeVertex(vArr[base + 2], ox1, oy1)
                writeVertex(vArr[base + 3], ox1, oy1)
                writeVertex(vArr[base + 4], ix0, iy0)
                writeVertex(vArr[base + 5], ix1, iy1)
            }
            SDL_RenderGeometry(inRenderer, null, vArr, vVerts, null, 0)
        }
    }

    private fun fillRect(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inX: Float,
        inY: Float,
        inW: Float,
        inH: Float,
    ) {
        if (inW <= 0f || inH <= 0f) return
        memScoped {
            val vRect = alloc<SDL_FRect>()
            vRect.x = inX; vRect.y = inY; vRect.w = inW; vRect.h = inH
            SDL_RenderFillRect(inRenderer, vRect.ptr)
        }
    }

    /* Rounded rect: middle band + two side strips + four corner fans.
       Corner fans are SDL_RenderGeometry triangles centred at each corner's
       inset. Segment count is fixed at 16 — good enough for small Material
       radii without burning CPU. */
    private fun fillRoundedRect(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inX: Float,
        inY: Float,
        inW: Float,
        inH: Float,
        inRadius: Float,
    ) {
        val vR = inRadius.coerceAtMost(kotlin.math.min(inW, inH) / 2f).coerceAtLeast(0f)
        if (vR <= 0.5f) { fillRect(inRenderer, inX, inY, inW, inH); return }

        // Body: horizontal full-width band, plus the two vertical side strips
        // above and below the band.
        fillRect(inRenderer, inX, inY + vR, inW, inH - 2f * vR)
        fillRect(inRenderer, inX + vR, inY, inW - 2f * vR, vR)
        fillRect(inRenderer, inX + vR, inY + inH - vR, inW - 2f * vR, vR)

        // Corner arcs as triangle fans.
        fillCornerArc(inRenderer, inX + vR,         inY + vR,         vR, PI.toFloat(), 1.5f * PI.toFloat())  // TL
        fillCornerArc(inRenderer, inX + inW - vR,   inY + vR,         vR, 1.5f * PI.toFloat(), 2f * PI.toFloat()) // TR
        fillCornerArc(inRenderer, inX + inW - vR,   inY + inH - vR,   vR, 0f, 0.5f * PI.toFloat())                 // BR
        fillCornerArc(inRenderer, inX + vR,         inY + inH - vR,   vR, 0.5f * PI.toFloat(), PI.toFloat())       // BL
    }

    private fun fillCornerArc(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inCx: Float,
        inCy: Float,
        inR: Float,
        inStartRad: Float,
        inEndRad: Float,
    ) {
        val kSegments = 16
        val vVerts = kSegments * 3
        memScoped {
            val vArr = allocArray<SDL_Vertex>(vVerts)
            for (i in 0 until kSegments) {
                val t0 = inStartRad + (inEndRad - inStartRad) * (i.toFloat() / kSegments)
                val t1 = inStartRad + (inEndRad - inStartRad) * ((i + 1).toFloat() / kSegments)
                val base = i * 3
                writeVertex(vArr[base + 0], inCx, inCy)
                writeVertex(vArr[base + 1], inCx + inR * cos(t0), inCy + inR * sin(t0))
                writeVertex(vArr[base + 2], inCx + inR * cos(t1), inCy + inR * sin(t1))
            }
            SDL_RenderGeometry(inRenderer, null, vArr, vVerts, null, 0)
        }
    }

    private fun writeVertex(inV: SDL_Vertex, inX: Float, inY: Float) {
        inV.position.x = inX
        inV.position.y = inY
        inV.color.r = fR
        inV.color.g = fG
        inV.color.b = fB
        inV.color.a = fA
        inV.tex_coord.x = 0f
        inV.tex_coord.y = 0f
    }

    private fun setColor(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inColor: ComposeColor,
    ) {
        fR = inColor.red
        fG = inColor.green
        fB = inColor.blue
        fA = inColor.alpha
        SDL_SetRenderDrawColor(
            inRenderer,
            inColor.r8.toUByte(),
            inColor.g8.toUByte(),
            inColor.b8.toUByte(),
            inColor.a8.toUByte(),
        )
    }
}
