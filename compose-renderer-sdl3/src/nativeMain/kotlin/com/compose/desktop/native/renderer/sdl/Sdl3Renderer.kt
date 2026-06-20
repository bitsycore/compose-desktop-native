package com.compose.desktop.native.renderer.sdl

import com.compose.desktop.native.*

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

   Curved edges (rounded-corner / circle fills and borders) get a ~1px
   alpha-feathered AA fringe so they don't look jagged at DPR 1 (e.g. a
   non-HiDPI Windows display); straight edges are axis-aligned and need none.
   Rounded corners are approximated with fans of triangles (16 segments). */
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

    /* Stroke quadrant: a solid band between inner/outer radii, plus an AA
       fringe feathering each curved edge to alpha 0 over ~1px. When the
       stroke is thinner than the fringe the solid band drops out and the two
       fringes meet — a thin antialiased line. */
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
        val vF = featherLogical()
        val vOuterSolid = inOuterR - vF * 0.5f
        val vOuterEdge = inOuterR + vF * 0.5f
        val vInnerSolid = inInnerR + vF * 0.5f
        val vInnerEdge = (inInnerR - vF * 0.5f).coerceAtLeast(0f)
        val vHasBand = vOuterSolid > vInnerSolid
        val vPerSeg = if (vHasBand) 18 else 12
        memScoped {
            val vArr = allocArray<SDL_Vertex>(kSegments * vPerSeg)
            var b = 0
            for (i in 0 until kSegments) {
                val t0 = inStartRad + (inEndRad - inStartRad) * (i.toFloat() / kSegments)
                val t1 = inStartRad + (inEndRad - inStartRad) * ((i + 1).toFloat() / kSegments)
                val c0 = cos(t0); val s0 = sin(t0)
                val c1 = cos(t1); val s1 = sin(t1)
                // outer fringe (solid edge → outside, fading out)
                radialQuad(vArr, b, inCx, inCy, vOuterSolid, fA, vOuterEdge, 0f, c0, s0, c1, s1); b += 6
                // inner fringe (solid edge → inside, fading out)
                radialQuad(vArr, b, inCx, inCy, vInnerSolid, fA, vInnerEdge, 0f, c0, s0, c1, s1); b += 6
                if (vHasBand) {
                    radialQuad(vArr, b, inCx, inCy, vInnerSolid, fA, vOuterSolid, fA, c0, s0, c1, s1); b += 6
                }
            }
            SDL_RenderGeometry(inRenderer, null, vArr, kSegments * vPerSeg, null, 0)
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

    /* Fill quadrant: a solid triangle fan to an inset radius, plus an AA
       fringe feathering the curved edge from full alpha (inset radius) to 0
       (just past the true radius) over ~1px. Straight radii meet the body
       rects and stay solid. */
    private fun fillCornerArc(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inCx: Float,
        inCy: Float,
        inR: Float,
        inStartRad: Float,
        inEndRad: Float,
    ) {
        val kSegments = 16
        val vF = featherLogical()
        val vRSolid = (inR - vF * 0.5f).coerceAtLeast(0f)
        val vROuter = inR + vF * 0.5f
        // per segment: 1 solid fan triangle (3 verts) + 1 fringe quad (6 verts)
        val vVerts = kSegments * 9
        memScoped {
            val vArr = allocArray<SDL_Vertex>(vVerts)
            var b = 0
            for (i in 0 until kSegments) {
                val t0 = inStartRad + (inEndRad - inStartRad) * (i.toFloat() / kSegments)
                val t1 = inStartRad + (inEndRad - inStartRad) * ((i + 1).toFloat() / kSegments)
                val c0 = cos(t0); val s0 = sin(t0)
                val c1 = cos(t1); val s1 = sin(t1)
                writeVertex(vArr[b + 0], inCx, inCy)
                writeVertex(vArr[b + 1], inCx + vRSolid * c0, inCy + vRSolid * s0)
                writeVertex(vArr[b + 2], inCx + vRSolid * c1, inCy + vRSolid * s1)
                b += 3
                radialQuad(vArr, b, inCx, inCy, vRSolid, fA, vROuter, 0f, c0, s0, c1, s1)
                b += 6
            }
            SDL_RenderGeometry(inRenderer, null, vArr, vVerts, null, 0)
        }
    }

    /* Emits two triangles (6 verts at inBase) for a radial quad spanning one
       arc segment: the inner edge sits at radius inRLo with alpha inAlphaLo,
       the outer edge at inRHi with inAlphaHi. Used for both the solid band and
       the alpha-feathered fringes. */
    private fun radialQuad(
        inArr: CArrayPointer<SDL_Vertex>,
        inBase: Int,
        inCx: Float,
        inCy: Float,
        inRLo: Float,
        inAlphaLo: Float,
        inRHi: Float,
        inAlphaHi: Float,
        inC0: Float,
        inS0: Float,
        inC1: Float,
        inS1: Float,
    ) {
        writeVertexA(inArr[inBase + 0], inCx + inRLo * inC0, inCy + inRLo * inS0, inAlphaLo)
        writeVertexA(inArr[inBase + 1], inCx + inRHi * inC0, inCy + inRHi * inS0, inAlphaHi)
        writeVertexA(inArr[inBase + 2], inCx + inRHi * inC1, inCy + inRHi * inS1, inAlphaHi)
        writeVertexA(inArr[inBase + 3], inCx + inRLo * inC0, inCy + inRLo * inS0, inAlphaLo)
        writeVertexA(inArr[inBase + 4], inCx + inRHi * inC1, inCy + inRHi * inS1, inAlphaHi)
        writeVertexA(inArr[inBase + 5], inCx + inRLo * inC1, inCy + inRLo * inS1, inAlphaLo)
    }

    /* AA fringe width in logical points ≈ 1 physical pixel after the
       renderer's SDL_SetRenderScale(dpr), so curved edges feather over one
       pixel at any DPR (most visible at DPR 1). */
    private fun featherLogical(): Float =
        (1f / backend.pixelDensity.coerceAtLeast(0.5f)).coerceIn(0.5f, 1.5f)

    private fun writeVertex(inV: SDL_Vertex, inX: Float, inY: Float) = writeVertexA(inV, inX, inY, fA)

    private fun writeVertexA(inV: SDL_Vertex, inX: Float, inY: Float, inAlpha: Float) {
        inV.position.x = inX
        inV.position.y = inY
        inV.color.r = fR
        inV.color.g = fG
        inV.color.b = fB
        inV.color.a = inAlpha
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
