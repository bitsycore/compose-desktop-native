package com.compose.desktop.native.renderer.skia
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip

import com.compose.desktop.native.element.BackgroundModifier
import com.compose.desktop.native.element.BorderModifier
import com.compose.desktop.native.element.ClipModifier
import com.compose.desktop.native.element.DrawBehindModifier
import com.compose.desktop.native.element.HorizontalScrollModifier
import com.compose.desktop.native.element.VerticalScrollModifier
import androidx.compose.ui.geometry.Size
import com.compose.desktop.native.element.GraphicsLayerModifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.compose.desktop.native.node.LayoutNode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

// ==================
// MARK: SkiaRenderer — draws the LayoutNode tree onto a Skia Canvas.
// ==================

class SkiaRenderer internal constructor(
    private val textRenderer: SkiaTextRenderer,
    private val imageCache: SkiaImageCache,
) {

    private val kClearColor = 0xFF121212.toInt() // matches Material dark background
    // Renderer paints in logical points; HiDPI scaling is applied separately in
    // beginFrame, so shapes resolve their outlines at density = 1.
    private val kShapeDensity = Density(1f)

    // Per-node cached subtree image, keyed by the GraphicsLayer's cacheKey.
    // Held by LayoutNode identity. Subject to a coarse cleanup pass each
    // frame (entries for nodes we didn't visit get evicted).
    private class CachedLayer(val key: Any, val w: Int, val h: Int, val image: Image)
    private val fCache = mutableMapOf<LayoutNode, CachedLayer>()
    private val fSeenThisFrame = mutableSetOf<LayoutNode>()

    // Logical window height for the current frame — used to cull off-screen
    // text lines. Set by draw(); Int.MAX_VALUE means "no culling".
    private var fViewportHeight = Int.MAX_VALUE

    fun draw(inRoot: LayoutNode, inCanvas: Canvas, inViewportHeight: Int = Int.MAX_VALUE) {
        fViewportHeight = inViewportHeight
        inCanvas.clear(kClearColor)
        fSeenThisFrame.clear()
        drawNode(inRoot, inCanvas)
        // Evict cached layers whose owning node didn't appear this frame.
        if (fCache.size > fSeenThisFrame.size) {
            val vIter = fCache.entries.iterator()
            while (vIter.hasNext()) {
                val vE = vIter.next()
                if (vE.key !in fSeenThisFrame) {
                    vE.value.image.close()
                    vIter.remove()
                }
            }
        }
    }

    /* Apply alpha + transform + cache from Modifier.graphicsLayer (and
       legacy AlphaModifier) around the draw of inNode's content. */
    private fun drawNode(inNode: LayoutNode, inCanvas: Canvas) {
        val vAlpha = inNode.nodeAlpha
        val vLayer = inNode.graphicsLayer

        // Cull fully off-screen leaves (or clip/scroll subtrees) so a huge
        // line-number gutter (one Text leaf per line) doesn't redraw thousands
        // of off-screen nodes every frame. Skipped when a transform could
        // translate the node back into view.
        if ((vLayer == null || !vLayer.needsTransform) &&
            (inNode.children.isEmpty() || clipsChildren(inNode))) {
            val vTop = inNode.absoluteY
            // Long math so a very large node height can't overflow into a negative.
            if (vTop.toLong() + inNode.height < 0L || vTop > fViewportHeight) return
        }

        val vWantsTransform = vLayer != null && vLayer.needsTransform
        val vWantsCache = vLayer != null && vLayer.cacheKey != null
        val vWantsAlpha = vAlpha < 1f

        if (!vWantsTransform && !vWantsCache && !vWantsAlpha) {
            drawNodeContent(inNode, inCanvas)
            return
        }

        // Open a transform save() if a non-identity transform is requested.
        if (vWantsTransform) {
            inCanvas.save()
            applyTransform(inNode, vLayer!!, inCanvas)
        }

        if (vWantsCache) {
            fSeenThisFrame.add(inNode)
            drawCachedSubtree(inNode, vLayer!!, vAlpha, inCanvas)
        } else if (vWantsAlpha) {
            val vPaint = Paint().apply { color = (vAlpha * 255f).toInt().coerceIn(0, 255) shl 24 }
            inCanvas.saveLayer(null, vPaint)
            drawNodeContent(inNode, inCanvas)
            inCanvas.restore()
            vPaint.close()
        } else {
            drawNodeContent(inNode, inCanvas)
        }

        if (vWantsTransform) inCanvas.restore()
    }

    /* True if the node clips its children to its own bounds (clip modifier or
       scroll viewport), so culling the whole subtree when off-screen is safe. */
    private fun clipsChildren(inNode: LayoutNode): Boolean {
        var v = false
        inNode.modifier.foldIn(Unit) { _, e ->
            if (e is ClipModifier || e is VerticalScrollModifier || e is HorizontalScrollModifier) v = true
        }
        return v
    }

    /* Pivot-aware scale / rotation / translation. Pivot is the node's
       absolute position offset by the requested fraction of its bounds. */
    private fun applyTransform(inNode: LayoutNode, inLayer: GraphicsLayerModifier, inCanvas: Canvas) {
        val vPivotX = inNode.absoluteX + inNode.width * inLayer.transformOrigin.pivotFractionX
        val vPivotY = inNode.absoluteY + inNode.height * inLayer.transformOrigin.pivotFractionY
        inCanvas.translate(vPivotX + inLayer.translationX, vPivotY + inLayer.translationY)
        if (inLayer.rotationZ != 0f) inCanvas.rotate(inLayer.rotationZ)
        if (inLayer.scaleX != 1f || inLayer.scaleY != 1f) inCanvas.scale(inLayer.scaleX, inLayer.scaleY)
        inCanvas.translate(-vPivotX, -vPivotY)
    }

    /* Caches the subtree as a Skia Image keyed by cacheKey + node size.
       On hit, blits the cached Image to inCanvas. The cached image is in
       node-local coords (origin at 0,0), so we translate by absX/absY
       to land it back at the node's position. Alpha applies to the blit. */
    private fun drawCachedSubtree(
        inNode: LayoutNode,
        inLayer: GraphicsLayerModifier,
        inAlpha: Float,
        inCanvas: Canvas,
    ) {
        val vW = inNode.width.coerceAtLeast(1)
        val vH = inNode.height.coerceAtLeast(1)
        val vKey = inLayer.cacheKey!!
        var vEntry = fCache[inNode]
        if (vEntry == null || vEntry.key != vKey || vEntry.w != vW || vEntry.h != vH) {
            vEntry?.image?.close()
            vEntry = renderToImage(inNode, vW, vH)?.let { CachedLayer(vKey, vW, vH, it) }
            if (vEntry != null) fCache[inNode] = vEntry else fCache.remove(inNode)
        }
        val vImage = vEntry?.image
        if (vImage == null) {
            // Surface creation failed; fall back to direct draw with alpha.
            if (inAlpha < 1f) {
                val vP = Paint().apply { color = (inAlpha * 255f).toInt().coerceIn(0, 255) shl 24 }
                inCanvas.saveLayer(null, vP); drawNodeContent(inNode, inCanvas); inCanvas.restore(); vP.close()
            } else drawNodeContent(inNode, inCanvas)
            return
        }
        val vPaint = if (inAlpha < 1f) {
            // White + alpha so drawImage only modulates opacity (no colour tint).
            val vA = (inAlpha * 255f).toInt().coerceIn(0, 255)
            Paint().apply { color = (vA shl 24) or 0x00FFFFFF }
        } else null
        inCanvas.drawImage(vImage, inNode.absoluteX.toFloat(), inNode.absoluteY.toFloat(), vPaint)
        vPaint?.close()
    }

    /* Draws the subtree into a fresh raster surface at node-local coords
       and snapshots an Image. Returns null on allocation failure. */
    private fun renderToImage(inNode: LayoutNode, inW: Int, inH: Int): Image? {
        val vSurface = try { Surface.makeRasterN32Premul(inW, inH) } catch (t: Throwable) { return null }
        // The cached content is rendered at the node's natural absolute
        // position; we then snapshot and re-draw it offset, so we need to
        // temporarily shift the canvas by -absX/-absY here.
        val vCanvas = vSurface.canvas
        vCanvas.save()
        vCanvas.translate(-inNode.absoluteX.toFloat(), -inNode.absoluteY.toFloat())
        drawNodeContent(inNode, vCanvas)
        vCanvas.restore()
        val vImage = vSurface.makeImageSnapshot()
        vSurface.close()
        return vImage
    }

    private fun drawNodeContent(inNode: LayoutNode, inCanvas: Canvas) {
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
                drawOutline(inCanvas, vAx, vAy, vW, vH, element.shape.createOutline(Size(vW, vH), LayoutDirection.Ltr, kShapeDensity), vPaint)
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
                    element.shape.createOutline(Size(vW, vH), LayoutDirection.Ltr, kShapeDensity),
                    vPaint,
                    cornerRadiusAdjust = -vInset
                )
                vPaint.close()
            }
        }

        // ============
        //  drawBehind modifier(s) — invoke each in modifier order. Runs
        //  after background / border so it sits on top of the chrome but
        //  under the node's text / image / children.
        inNode.modifier.foldIn(Unit) { _, element ->
            if (element is DrawBehindModifier) {
                val vScope = SkiaDrawScope(
                    fCanvas = inCanvas,
                    fOriginX = vAx,
                    fOriginY = vAy,
                    size = Size(vW, vH),
                )
                element.onDraw(vScope)
            }
        }

        // ============
        //  Canvas {} leaf — drawer set by the Canvas composable. Invoked
        //  with the node's bounds as size.
        val vDrawer = inNode.drawer
        if (vDrawer != null) {
            val vScope = SkiaDrawScope(
                fCanvas = inCanvas,
                fOriginX = vAx,
                fOriginY = vAy,
                size = Size(vW, vH),
            )
            vDrawer(vScope)
        }

        // ============
        //  Text leaf
        val vText = inNode.text
        if (!vText.isNullOrEmpty()) {
            // Reuse the exact wrap the measure pass cached on the node (don't
            // re-wrap at a different width — that thrashed the cache and
            // re-wrapped a huge body every frame; see LayoutNode.cachedWrap).
            val vWrapped = inNode.cachedWrap()
            textRenderer.drawText(
                inCanvas, vText,
                vAx, vAy, inNode.width, inNode.height,
                inNode.textColor, inNode.fontSize, inNode.textAlign,
                inNode.softWrap,
                inNode.fontFamily,
                inNode.fontVariationSettings,
                inNode.textSpans,
                vWrapped,
                0f, fViewportHeight.toFloat(),
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
        // Sort children by zIndex (stable: when none set, draw in tree order).
        val vChildren = if (inNode.children.any { it.zIndex != 0f })
            inNode.children.sortedBy { it.zIndex } else inNode.children
        if (vChildClip != null && inNode.children.isNotEmpty()) {
            inCanvas.save()
            applyClip(inCanvas, vAx, vAy, vW, vH, vChildClip.createOutline(Size(vW, vH), LayoutDirection.Ltr, kShapeDensity))
            for (child in vChildren) drawNode(child, inCanvas)
            inCanvas.restore()
        } else {
            for (child in vChildren) drawNode(child, inCanvas)
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
            is Outline.Rounded -> {
                val vRad = inOutline.roundRect.bottomLeftCornerRadius.x
                if (vRad <= 0f) {
                    inCanvas.clipRect(Rect.makeXYWH(inX, inY, inW, inH), antiAlias = true)
                } else {
                    inCanvas.clipRRect(RRect.makeXYWH(inX, inY, inW, inH, vRad), antiAlias = true)
                }
            }
            is Outline.Generic -> {
                // Generic paths aren't used by the built-in shapes; skip clip.
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
            is Outline.Rounded -> {
                val vRad = (inOutline.roundRect.bottomLeftCornerRadius.x + cornerRadiusAdjust).coerceAtLeast(0f)
                if (vRad <= 0f) {
                    inCanvas.drawRect(Rect.makeXYWH(inX, inY, inW, inH), inPaint)
                } else {
                    inCanvas.drawRRect(RRect.makeXYWH(inX, inY, inW, inH, vRad), inPaint)
                }
            }
            is Outline.Generic -> {
                // No built-in shape produces Generic outlines yet.
            }
        }
    }
}
