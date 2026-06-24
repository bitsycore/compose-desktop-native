package com.compose.desktop.native.renderer.sdl
import androidx.compose.ui.zIndex

import com.compose.desktop.native.*

import androidx.compose.ui.BackgroundModifier
import androidx.compose.ui.BorderModifier
import androidx.compose.ui.ClipModifier
import androidx.compose.ui.HorizontalScrollModifier
import androidx.compose.ui.VerticalScrollModifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.GraphicsLayerModifier
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

    // Offscreen layer pool for Modifier.alpha + graphicsLayer transforms.
    // Each entry is a window-physical-sized TARGET texture; pool grows with
    // nesting depth and is recreated when the window size changes.
    private val fLayerTargets = mutableListOf<COpaquePointer>()
    private var fLayerDepth = 0
    private var fLayerW = 0
    private var fLayerH = 0

    // Persistent per-node cache: subtree pre-rendered into a window-sized
    // texture, reused frame to frame while cacheKey matches. Window-sized
    // (rather than node-sized) because SDL3 has no canvas-translate; the
    // subtree paints at its absolute coords inside the texture. We track
    // those original coords (srcX/srcY) so subsequent blits can pull from
    // the right region even when the node has scrolled to a new position.
    private class CachedLayer(
        val key: Any,
        val texture: COpaquePointer,
        val srcX: Int,
        val srcY: Int,
        val srcW: Int,
        val srcH: Int,
        val texW: Int,
        val texH: Int,
    )
    private val fCache = mutableMapOf<LayoutNode, CachedLayer>()
    private val fSeenThisFrame = mutableSetOf<LayoutNode>()

    fun draw(inRoot: LayoutNode) {
        val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        SDL_SetRenderDrawColor(vRenderer, kClearR, kClearG, kClearB, 0xFFu)
        SDL_RenderClear(vRenderer)
        fSeenThisFrame.clear()
        drawNode(inRoot)
        // Evict cache entries for nodes we didn't paint this frame.
        if (fCache.size > fSeenThisFrame.size) {
            val vIter = fCache.entries.iterator()
            while (vIter.hasNext()) {
                val vE = vIter.next()
                if (vE.key !in fSeenThisFrame) {
                    SDL_DestroyTexture(vE.value.texture.reinterpret())
                    vIter.remove()
                }
            }
        }
    }

    private fun drawNode(inNode: LayoutNode) {
        val vAlpha = inNode.nodeAlpha
        val vLayer = inNode.graphicsLayer
        val vWantsTransform = vLayer != null && vLayer.needsTransform
        val vWantsCache = vLayer != null && vLayer.cacheKey != null
        val vWantsAlpha = vAlpha < 1f

        when {
            vWantsCache              -> drawNodeCached(inNode, vLayer!!, vAlpha)
            vWantsTransform          -> drawNodeTransformed(inNode, vLayer!!, vAlpha)
            vWantsAlpha              -> drawNodeLayered(inNode, vAlpha)
            else                     -> drawNodeContent(inNode)
        }
    }

    /* Renders the node's subtree into an offscreen texture, then composites it
       back at inAlpha so overlapping content fades as a single layer (no
       double-blend). Falls back to opaque drawing if the target can't be made. */
    private fun drawNodeLayered(inNode: LayoutNode, inAlpha: Float) {
        val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        val vLayer = acquireLayer(vRenderer) ?: run { drawNodeContent(inNode); return }
        val vDpr = backend.pixelDensity
        val vPrevTarget = SDL_GetRenderTarget(vRenderer)

        fLayerDepth++
        SDL_SetRenderTarget(vRenderer, vLayer.reinterpret())
        SDL_SetRenderScale(vRenderer, vDpr, vDpr)          // a target switch can reset the scale
        SDL_SetRenderDrawColor(vRenderer, 0u, 0u, 0u, 0u)  // transparent
        SDL_RenderClear(vRenderer)
        drawNodeContent(inNode)
        fLayerDepth--

        SDL_SetRenderTarget(vRenderer, vPrevTarget)        // window, or the enclosing layer
        SDL_SetRenderScale(vRenderer, vDpr, vDpr)
        SDL_SetTextureAlphaMod(vLayer.reinterpret(), (inAlpha * 255f).toInt().coerceIn(0, 255).toUByte())
        memScoped {
            // The layer is window-physical-sized; drawing it at the logical
            // window size (× dpr via the render scale) maps it back 1:1.
            val vDst = alloc<SDL_FRect>()
            vDst.x = 0f; vDst.y = 0f
            vDst.w = backend.windowWidth.toFloat(); vDst.h = backend.windowHeight.toFloat()
            SDL_RenderTexture(vRenderer, vLayer.reinterpret(), null, vDst.ptr)
        }
    }

    /* Window-physical-sized TARGET texture for the current nesting depth;
       recreates the pool when the window size changes. */
    private fun acquireLayer(inRenderer: CPointer<cnames.structs.SDL_Renderer>): COpaquePointer? {
        val vW = backend.pixelWidth
        val vH = backend.pixelHeight
        if (vW <= 0 || vH <= 0) return null
        if (vW != fLayerW || vH != fLayerH) {
            for (vT in fLayerTargets) SDL_DestroyTexture(vT.reinterpret())
            fLayerTargets.clear()
            fLayerW = vW; fLayerH = vH
        }
        while (fLayerTargets.size <= fLayerDepth) {
            val vTex = SDL_CreateTexture(
                inRenderer,
                SDL_PIXELFORMAT_RGBA32,
                SDL_TextureAccess.SDL_TEXTUREACCESS_TARGET,
                vW,
                vH,
            ) ?: return null
            SDL_SetTextureBlendMode(vTex.reinterpret(), SDL_BLENDMODE_BLEND)
            fLayerTargets.add(vTex)
        }
        return fLayerTargets[fLayerDepth]
    }

    fun destroy() {
        for (vT in fLayerTargets) SDL_DestroyTexture(vT.reinterpret())
        fLayerTargets.clear()
        for (vE in fCache.values) SDL_DestroyTexture(vE.texture.reinterpret())
        fCache.clear()
    }

    // ============
    //  Transform: render subtree to an ephemeral window-sized layer, then
    //  blit just the node's region with SDL_RenderTextureRotated to apply
    //  scale / rotation / translation / alpha.

    private fun drawNodeTransformed(inNode: LayoutNode, inLayer: GraphicsLayerModifier, inAlpha: Float) {
        val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        val vTex = acquireLayer(vRenderer) ?: run { drawNodeContent(inNode); return }
        renderSubtreeToTarget(vRenderer, vTex, inNode)
        // Ephemeral path: the texture was just painted at the node's current
        // absolute coords, so src = dst position-wise (no scroll drift to worry about).
        blitTransformed(vRenderer, vTex, inNode, inLayer, inAlpha, inCachedSrc = null)
    }

    // ============
    //  Cache: persistent per-node texture, rebuilt when cacheKey changes
    //  or the window resizes. Reused as-is on subsequent frames.

    private fun drawNodeCached(inNode: LayoutNode, inLayer: GraphicsLayerModifier, inAlpha: Float) {
        val vRenderer = backend.renderer?.reinterpret<cnames.structs.SDL_Renderer>() ?: return
        fSeenThisFrame.add(inNode)
        val vKey = inLayer.cacheKey!!
        val vWinW = backend.pixelWidth
        val vWinH = backend.pixelHeight
        var vEntry = fCache[inNode]
        if (vEntry == null || vEntry.key != vKey || vEntry.texW != vWinW || vEntry.texH != vWinH) {
            vEntry?.let { SDL_DestroyTexture(it.texture.reinterpret()) }
            fCache.remove(inNode)
            val vTex = createWindowSizedTarget(vRenderer) ?: run {
                // Allocation failed — fall through to non-cached transform path.
                if (inLayer.needsTransform) drawNodeTransformed(inNode, inLayer, inAlpha)
                else drawNodeContent(inNode)
                return
            }
            renderSubtreeToTarget(vRenderer, vTex, inNode)
            vEntry = CachedLayer(
                key = vKey, texture = vTex,
                srcX = inNode.absoluteX, srcY = inNode.absoluteY,
                srcW = inNode.width,     srcH = inNode.height,
                texW = vWinW,            texH = vWinH,
            )
            fCache[inNode] = vEntry
        }
        blitTransformed(vRenderer, vEntry.texture, inNode, inLayer, inAlpha, vEntry)
    }

    /* Render the node's subtree into a target texture at the node's
       absolute coords. The target is cleared transparent first. The
       active render scale (= DPR) is preserved across the target switch. */
    private fun renderSubtreeToTarget(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inTarget: COpaquePointer,
        inNode: LayoutNode,
    ) {
        val vDpr = backend.pixelDensity
        val vPrev = SDL_GetRenderTarget(inRenderer)
        fLayerDepth++
        SDL_SetRenderTarget(inRenderer, inTarget.reinterpret())
        SDL_SetRenderScale(inRenderer, vDpr, vDpr)
        SDL_SetRenderDrawColor(inRenderer, 0u, 0u, 0u, 0u)
        SDL_RenderClear(inRenderer)
        drawNodeContent(inNode)
        fLayerDepth--
        SDL_SetRenderTarget(inRenderer, vPrev)
        SDL_SetRenderScale(inRenderer, vDpr, vDpr)
    }

    /* Blit the node region from inSource onto the current render target,
       applying the layer's scale / rotation / translation and alpha.
       inCachedSrc is set when blitting from a persistent cache: its
       srcX/srcY locate the original render position in the cache texture
       (which may differ from the node's CURRENT absoluteX/Y if the node
       has since been scrolled or otherwise moved). When null (ephemeral
       layer) the source is just rendered at the node's current absolute
       coords so src == dst position-wise. */
    private fun blitTransformed(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
        inSource: COpaquePointer,
        inNode: LayoutNode,
        inLayer: GraphicsLayerModifier,
        inAlpha: Float,
        inCachedSrc: CachedLayer?,
    ) {
        val vDpr = backend.pixelDensity
        val vAx = inNode.absoluteX.toFloat()
        val vAy = inNode.absoluteY.toFloat()
        val vW = inNode.width.toFloat()
        val vH = inNode.height.toFloat()
        val vScaledW = vW * inLayer.scaleX
        val vScaledH = vH * inLayer.scaleY
        val vPivotX = vAx + vW * inLayer.transformOrigin.pivotFractionX
        val vPivotY = vAy + vH * inLayer.transformOrigin.pivotFractionY
        // After scaling around the pivot, the new top-left is offset by
        // (pivot - scaled_pivot) where scaled_pivot is the same fraction of
        // the post-scale bounds. Translation is added on top.
        val vDstX = vPivotX - vScaledW * inLayer.transformOrigin.pivotFractionX + inLayer.translationX
        val vDstY = vPivotY - vScaledH * inLayer.transformOrigin.pivotFractionY + inLayer.translationY

        // Source rect: cached-layer entries remember where the subtree was
        // actually painted in the texture; ephemerals match current pos.
        val vSrcX = (inCachedSrc?.srcX?.toFloat() ?: vAx) * vDpr
        val vSrcY = (inCachedSrc?.srcY?.toFloat() ?: vAy) * vDpr
        val vSrcW = (inCachedSrc?.srcW?.toFloat() ?: vW) * vDpr
        val vSrcH = (inCachedSrc?.srcH?.toFloat() ?: vH) * vDpr

        SDL_SetTextureAlphaMod(inSource.reinterpret(),
            (inAlpha * 255f).toInt().coerceIn(0, 255).toUByte())

        memScoped {
            val vSrc = alloc<SDL_FRect>().apply {
                x = vSrcX; y = vSrcY
                w = vSrcW; h = vSrcH
            }
            val vDst = alloc<SDL_FRect>().apply {
                x = vDstX; y = vDstY
                w = vScaledW; h = vScaledH
            }
            if (inLayer.rotationZ == 0f) {
                SDL_RenderTexture(inRenderer, inSource.reinterpret(), vSrc.ptr, vDst.ptr)
            } else {
                val vCenter = alloc<SDL_FPoint>().apply {
                    x = vScaledW * inLayer.transformOrigin.pivotFractionX
                    y = vScaledH * inLayer.transformOrigin.pivotFractionY
                }
                SDL_RenderTextureRotated(
                    inRenderer, inSource.reinterpret(),
                    vSrc.ptr, vDst.ptr,
                    inLayer.rotationZ.toDouble(),
                    vCenter.ptr,
                    SDL_FLIP_NONE,
                )
            }
        }
    }

    private fun createWindowSizedTarget(
        inRenderer: CPointer<cnames.structs.SDL_Renderer>,
    ): COpaquePointer? {
        val vW = backend.pixelWidth
        val vH = backend.pixelHeight
        if (vW <= 0 || vH <= 0) return null
        val vTex = SDL_CreateTexture(
            inRenderer,
            SDL_PIXELFORMAT_RGBA32,
            SDL_TextureAccess.SDL_TEXTUREACCESS_TARGET,
            vW, vH,
        ) ?: return null
        SDL_SetTextureBlendMode(vTex.reinterpret(), SDL_BLENDMODE_BLEND)
        return vTex
    }

    private fun drawNodeContent(inNode: LayoutNode) {
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
        //  drawBehind modifier(s) — invoke each in modifier order, after
        //  background / border so they sit on top of the chrome but under
        //  text / image / children. flush()+release() between each scope
        //  so per-modifier draw order is preserved (later modifiers paint
        //  on top of earlier ones).
        inNode.modifier.foldIn(Unit) { _, element ->
            if (element is androidx.compose.ui.DrawBehindModifier) {
                val vScope = Sdl3DrawScope(
                    fRenderer = vRenderer,
                    fOriginX = vAx,
                    fOriginY = vAy,
                    size = androidx.compose.ui.geometry.Size(vW, vH),
                )
                element.onDraw(vScope)
                vScope.release()
            }
        }

        // ============
        //  Canvas{} leaf — drawer set by the Canvas composable. Batched
        //  triangles for the whole drawer block submit in one
        //  SDL_RenderGeometry call.
        val vDrawer = inNode.drawer
        if (vDrawer != null) {
            val vScope = Sdl3DrawScope(
                fRenderer = vRenderer,
                fOriginX = vAx,
                fOriginY = vAy,
                size = androidx.compose.ui.geometry.Size(vW, vH),
            )
            vDrawer(vScope)
            vScope.release()
        }

        // ============
        //  Text leaf
        val vText = inNode.text
        if (!vText.isNullOrEmpty()) {
            // Word-wrap to the box width so multi-line text renders the
            // same lines the layout pass measured against. softWrap = false
            // (e.g. a singleLine field) must NOT wrap — it stays one line and
            // overflows, matching the measure pass and the cursor math.
            val vWrapWidth = if (inNode.softWrap) inNode.width else Int.MAX_VALUE
            val vLines = textRenderer.textMeasurer.wrap(vText, inNode.fontSize, vWrapWidth, inNode.fontFamily).lines
            val vLineHeight = textRenderer.textMeasurer.lineHeight(inNode.fontSize, inNode.fontFamily).toInt()
            if (vLines.size == 1 && '\n' !in vText) {
                textRenderer.drawText(
                    vLines[0],
                    inNode.absoluteX, inNode.absoluteY,
                    inNode.width, inNode.height,
                    inNode.textColor, inNode.fontSize, inNode.textAlign,
                    inNode.fontFamily,
                    inNode.fontVariationSettings,
                )
            } else {
                for ((idx, line) in vLines.withIndex()) {
                    val vSlotTop = inNode.absoluteY + idx * vLineHeight
                    textRenderer.drawText(
                        line,
                        inNode.absoluteX, vSlotTop,
                        inNode.width, vLineHeight,
                        inNode.textColor, inNode.fontSize, inNode.textAlign,
                        inNode.fontFamily,
                        inNode.fontVariationSettings,
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
            // Nest correctly: intersect with the clip already in effect and
            // restore it afterwards, so a scroll viewport's clip survives a
            // child that also clips (e.g. each tab in a clipped tab strip, or a
            // BasicTextField clipping to its own taller-than-viewport bounds).
            // Without this the inner clip clobbers the outer one and content
            // spills past the scroll area.
            memScoped {
                val vHadClip = SDL_RenderClipEnabled(vRenderer)
                val vPrev = alloc<SDL_Rect>()
                SDL_GetRenderClipRect(vRenderer, vPrev.ptr)

                var vLeft = inNode.absoluteX
                var vTop = inNode.absoluteY
                var vRight = vLeft + inNode.width
                var vBottom = vTop + inNode.height
                if (vHadClip) {
                    vLeft = maxOf(vLeft, vPrev.x)
                    vTop = maxOf(vTop, vPrev.y)
                    vRight = minOf(vRight, vPrev.x + vPrev.w)
                    vBottom = minOf(vBottom, vPrev.y + vPrev.h)
                }

                val vRect = alloc<SDL_Rect>()
                vRect.x = vLeft
                vRect.y = vTop
                vRect.w = maxOf(0, vRight - vLeft)
                vRect.h = maxOf(0, vBottom - vTop)
                SDL_SetRenderClipRect(vRenderer, vRect.ptr)

                for (child in zOrderedChildren(inNode)) drawNode(child)

                if (vHadClip) SDL_SetRenderClipRect(vRenderer, vPrev.ptr)
                else SDL_SetRenderClipRect(vRenderer, null)
            }
        } else {
            for (child in zOrderedChildren(inNode)) drawNode(child)
        }
    }

    /* Sort children by Modifier.zIndex when present; default to tree
       order so the common case has zero overhead. */
    private fun zOrderedChildren(inNode: LayoutNode): List<LayoutNode> =
        if (inNode.children.any { it.zIndex != 0f })
            inNode.children.sortedBy { it.zIndex } else inNode.children

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
