package com.compose.desktop.native.renderer.skia

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
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect

// ==================
// MARK: SkiaRenderer — draws the LayoutNode tree onto a Skia Canvas.
// ==================

class SkiaRenderer(
    private val textRenderer: SkiaTextRenderer,
    private val imageCache: SkiaImageCache,
) {

    private val kClearColor = 0xFF121212.toInt() // matches Material dark background

    fun draw(inRoot: LayoutNode, inCanvas: Canvas) {
        inCanvas.clear(kClearColor)
        drawNode(inRoot, inCanvas)
    }

    private fun drawNode(inNode: LayoutNode, inCanvas: Canvas) {
        val vAx = inNode.absoluteX.toFloat()
        val vAy = inNode.absoluteY.toFloat()
        val vW = inNode.width.toFloat()
        val vH = inNode.height.toFloat()

        // ============
        //  Background
        inNode.modifier.foldIn(Unit) { _, element ->
            if (element is BackgroundModifier && element.color.alpha > 0f) {
                val vPaint = Paint().apply {
                    color = toSkiaColor(element.color)
                    isAntiAlias = true
                    mode = PaintMode.FILL
                }
                drawOutline(inCanvas, vAx, vAy, vW, vH, element.shape.outline(inNode.width, inNode.height), vPaint)
                vPaint.close()
            }
        }

        // ============
        //  Border
        inNode.modifier.foldIn(Unit) { _, element ->
            if (element is BorderModifier && element.width > 0 && element.color.alpha > 0f) {
                val vPaint = Paint().apply {
                    color = toSkiaColor(element.color)
                    isAntiAlias = true
                    mode = PaintMode.STROKE
                    strokeWidth = element.width.toFloat()
                }
                // Stroke is centred on the edge, so inset by half the stroke width to
                // keep the visual border fully inside the laid-out bounds.
                val vInset = element.width / 2f
                drawOutline(
                    inCanvas,
                    vAx + vInset, vAy + vInset,
                    vW - element.width.toFloat(), vH - element.width.toFloat(),
                    element.shape.outline(inNode.width, inNode.height),
                    vPaint,
                    cornerRadiusAdjust = -vInset
                )
                vPaint.close()
            }
        }

        // ============
        //  Text leaf
        val vText = inNode.text
        if (!vText.isNullOrEmpty()) {
            textRenderer.drawText(
                inCanvas, vText,
                vAx, vAy, inNode.width, inNode.height,
                inNode.textColor, inNode.fontSize, inNode.textAlign
            )
        }

        // ============
        //  Image leaf (decoded + cached by SkiaImageCache, painted per
        //  contentScale / alpha into the node bounds)
        val vPainter = inNode.painter
        if (vPainter != null) {
            imageCache.draw(
                inCanvas, vPainter.resourcePath, vPainter.kind,
                vAx, vAy, vW, vH,
                inNode.contentScale, inNode.imageAlpha,
            )
        }

        // ============
        //  Children (clipped to shape if Modifier.clip is present, or auto-
        //  clipped to the node bounds when a scroll modifier is present so
        //  scrolled-out content doesn't leak outside the viewport).
        var vClipShape: Shape? = null
        inNode.modifier.foldIn(Unit) { _, element ->
            when {
                element is ClipModifier -> vClipShape = element.shape
                element is VerticalScrollModifier && vClipShape == null   -> vClipShape = RectangleShape
                element is HorizontalScrollModifier && vClipShape == null -> vClipShape = RectangleShape
            }
        }
        val vChildClip = vClipShape
        if (vChildClip != null && inNode.children.isNotEmpty()) {
            inCanvas.save()
            applyClip(inCanvas, vAx, vAy, vW, vH, vChildClip.outline(inNode.width, inNode.height))
            for (child in inNode.children) drawNode(child, inCanvas)
            inCanvas.restore()
        } else {
            for (child in inNode.children) drawNode(child, inCanvas)
        }
    }

    private fun applyClip(
        inCanvas: Canvas,
        inX: Float,
        inY: Float,
        inW: Float,
        inH: Float,
        inOutline: Outline
    ) {
        when (inOutline) {
            is Outline.Rectangle -> {
                inCanvas.clipRect(Rect.makeXYWH(inX, inY, inW, inH), antiAlias = true)
            }
            is Outline.RoundedRect -> {
                val vRad = inOutline.cornerRadius.toFloat()
                if (vRad <= 0f) {
                    inCanvas.clipRect(Rect.makeXYWH(inX, inY, inW, inH), antiAlias = true)
                } else {
                    inCanvas.clipRRect(RRect.makeXYWH(inX, inY, inW, inH, vRad), antiAlias = true)
                }
            }
        }
    }

    private fun drawOutline(
        inCanvas: Canvas,
        inX: Float,
        inY: Float,
        inW: Float,
        inH: Float,
        inOutline: Outline,
        inPaint: Paint,
        cornerRadiusAdjust: Float = 0f
    ) {
        if (inW <= 0f || inH <= 0f) return
        when (inOutline) {
            is Outline.Rectangle -> {
                inCanvas.drawRect(Rect.makeXYWH(inX, inY, inW, inH), inPaint)
            }
            is Outline.RoundedRect -> {
                val vRad = (inOutline.cornerRadius.toFloat() + cornerRadiusAdjust).coerceAtLeast(0f)
                if (vRad <= 0f) {
                    inCanvas.drawRect(Rect.makeXYWH(inX, inY, inW, inH), inPaint)
                } else {
                    inCanvas.drawRRect(RRect.makeXYWH(inX, inY, inW, inH, vRad), inPaint)
                }
            }
        }
    }
}
