package com.compose.desktop.native.node

import androidx.compose.ui.*
import com.compose.desktop.native.element.*
import com.compose.desktop.native.element.FocusableModifier
import com.compose.desktop.native.element.GloballyPositionedModifier
import androidx.compose.ui.unit.IntOffset
import com.compose.desktop.native.element.HorizontalScrollModifier
import com.compose.desktop.native.element.HoverableModifier
import com.compose.desktop.native.element.OnDragModifier
import com.compose.desktop.native.element.OnKeyEventModifier
import com.compose.desktop.native.element.OnTextInputModifier
import com.compose.desktop.native.element.PressableModifier
import com.compose.desktop.native.element.VerticalScrollModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: LayoutNode
// ==================

class LayoutNode : androidx.compose.ui.semantics.SemanticsInfo {
    var parent: LayoutNode? = null
    val children = mutableListOf<LayoutNode>()

    /**
     * Modifier chain attached to this layout. The setter rebuilds
     * [nodes] (the Modifier.Node chain) on every assignment so that
     * vendored DelegatableNode traversals see live nodes. The renderer
     * still walks `modifier.foldIn` directly — Phase 4i scaffolding.
     */
    var modifier: Modifier = Modifier
        set(value) {
            if (field === value) return
            field = value
            nodes.update(value)
            recomputeChainCaches()
        }

    // ============
    //  Phase 7 — per-modifier-assignment caches
    //
    //  Every property here is materialised once in [recomputeChainCaches]
    //  when `modifier =` runs, instead of foldIn'ing per-frame. The
    //  per-frame layout + render hot paths used to walk the modifier
    //  chain for padding / offset / alpha / zIndex / graphicsLayer /
    //  size / scroll-state / click+drag+hover+focus handlers / key+text
    //  dispatch / layout-modifier — every one of those reads is now a
    //  plain field load.
    //
    //  What's NOT cached: anything that reads `state.value` inside a
    //  modifier (e.g. `HorizontalScrollModifier.state.value` —
    //  ScrollState.value mutates inside the same modifier instance).
    //  For those we cache the state reference itself, and call sites
    //  read `cachedHorizontalScrollState?.value` live.

    // Layout-side caches (read during measure/place).
    private var cachedPaddingLeft: Int = 0
    private var cachedPaddingTop: Int = 0
    private var cachedPaddingRight: Int = 0
    private var cachedPaddingBottom: Int = 0
    private var cachedOffsetX: Int = 0
    private var cachedOffsetY: Int = 0
    private var cachedZIndex: Float = 0f
    private var cachedNodeAlpha: Float = 1f
    private var cachedGraphicsLayer: com.compose.desktop.native.element.GraphicsLayerModifier? = null
    /** Stacked size modifiers in foldIn order — `.height(48.dp).width(100.dp)` creates TWO; each constrains in turn. */
    private var cachedSizes: List<SizeModifier> = emptyList()
    private var cachedLayoutModifier: androidx.compose.ui.layout.LayoutModifierElement? = null
    private var cachedHorizontalScrollState: androidx.compose.foundation.ScrollState? = null
    private var cachedVerticalScrollState: androidx.compose.foundation.ScrollState? = null

    // Event-side caches (read during ComposeWindow's pointer / key / text dispatch).
    private var cachedClickable: ClickableModifier? = null
    private var cachedSecondaryClickable: SecondaryClickModifier? = null
    private var cachedMiddleClickable: MiddleClickModifier? = null
    private var cachedHoverables: List<HoverableModifier> = emptyList()
    private var cachedPressable: PressableModifier? = null
    private var cachedDraggable: OnDragModifier? = null
    private var cachedFocusable: FocusableModifier? = null
    /** Multiple OnKeyEvent handlers per node are valid — dispatch tries each in chain order. */
    private var cachedKeyEventHandlers: List<(androidx.compose.ui.input.key.KeyEvent) -> Boolean> = emptyList()
    private var cachedTextInputHandler: ((String) -> Unit)? = null
    /** Multiple onGloballyPositioned callbacks are valid — all fire in chain order. */
    private var cachedGloballyPositionedList: List<GloballyPositionedModifier> = emptyList()

    private fun recomputeChainCaches() {
        var padL = 0; var padT = 0; var padR = 0; var padB = 0
        var offX = 0; var offY = 0
        var zIdx = 0f
        var alpha = 1f
        var gl: com.compose.desktop.native.element.GraphicsLayerModifier? = null
        var sizes: MutableList<SizeModifier>? = null
        var layoutMod: androidx.compose.ui.layout.LayoutModifierElement? = null
        var hScroll: androidx.compose.foundation.ScrollState? = null
        var vScroll: androidx.compose.foundation.ScrollState? = null
        var click: ClickableModifier? = null
        var click2: SecondaryClickModifier? = null
        var click3: MiddleClickModifier? = null
        var hovers: MutableList<HoverableModifier>? = null
        var press: PressableModifier? = null
        var drag: OnDragModifier? = null
        var focus: FocusableModifier? = null
        var keys: MutableList<(androidx.compose.ui.input.key.KeyEvent) -> Boolean>? = null
        var text: ((String) -> Unit)? = null
        var positions: MutableList<GloballyPositionedModifier>? = null
        modifier.foldIn(Unit) { _, e ->
            when (e) {
                is PaddingModifier                                                  -> { padL += e.start; padT += e.top; padR += e.end; padB += e.bottom }
                is OffsetModifier                                                   -> { offX += e.x; offY += e.y }
                is androidx.compose.ui.ZIndexElement                                -> zIdx += e.zIndex
                is AlphaModifier                                                    -> alpha *= e.alpha
                is com.compose.desktop.native.element.GraphicsLayerModifier         -> { alpha *= e.alpha; gl = e }
                is SizeModifier                                                     -> { (sizes ?: mutableListOf<SizeModifier>().also { sizes = it }).add(e) }
                is androidx.compose.ui.layout.LayoutModifierElement                 -> layoutMod = e
                is HorizontalScrollModifier                                         -> { if (hScroll == null) hScroll = e.state }
                is VerticalScrollModifier                                           -> { if (vScroll == null) vScroll = e.state }
                is ClickableModifier                                                -> click = e
                is SecondaryClickModifier                                           -> click2 = e
                is MiddleClickModifier                                              -> click3 = e
                is HoverableModifier                                                -> { (hovers ?: mutableListOf<HoverableModifier>().also { hovers = it }).add(e) }
                is PressableModifier                                                -> { if (press == null) press = e }
                is OnDragModifier                                                   -> { if (drag == null) drag = e }
                is FocusableModifier                                                -> { if (focus == null) focus = e }
                is OnKeyEventModifier                                               -> { (keys ?: mutableListOf<(androidx.compose.ui.input.key.KeyEvent) -> Boolean>().also { keys = it }).add(e.handler) }
                is OnTextInputModifier                                              -> { if (text == null) text = e.handler }
                is GloballyPositionedModifier                                       -> { (positions ?: mutableListOf<GloballyPositionedModifier>().also { positions = it }).add(e) }
            }
        }
        cachedPaddingLeft = padL; cachedPaddingTop = padT
        cachedPaddingRight = padR; cachedPaddingBottom = padB
        cachedOffsetX = offX; cachedOffsetY = offY
        cachedZIndex = zIdx
        cachedNodeAlpha = alpha
        cachedGraphicsLayer = gl
        cachedSizes = sizes ?: emptyList()
        cachedLayoutModifier = layoutMod
        cachedHorizontalScrollState = hScroll
        cachedVerticalScrollState = vScroll
        cachedClickable = click
        cachedSecondaryClickable = click2
        cachedMiddleClickable = click3
        cachedHoverables = hovers ?: emptyList()
        cachedPressable = press
        cachedDraggable = drag
        cachedFocusable = focus
        cachedKeyEventHandlers = keys ?: emptyList()
        cachedTextInputHandler = text
        cachedGloballyPositionedList = positions ?: emptyList()
    }

    internal var measurePolicy: MeasurePolicy = DefaultMeasurePolicy

    // ============
    //  Phase 4 node-engine bring-up — see NODE_ENGINE_PORT.md.
    //  NodeChain + NodeCoordinator are real (not shims) but the renderer
    //  doesn't drive Modifier.Node lifecycle yet. Delete in a future phase
    //  when our LayoutNode is replaced by upstream's (Phase 5+).
    internal val nodes: androidx.compose.ui.node.NodeChain = androidx.compose.ui.node.NodeChain(this)
    internal val coordinator: androidx.compose.ui.node.NodeCoordinator = androidx.compose.ui.node.NodeCoordinator(this)
    internal val _children get() = children
    internal val zSortedChildren get() = children
    internal var owner: androidx.compose.ui.node.Owner? = null

    // LayoutInfo (via SemanticsInfo) members — all defaulted to stubs
    // since no engine code reads them in Phase 1.
    override val coordinates: androidx.compose.ui.layout.LayoutCoordinates =
        object : androidx.compose.ui.layout.LayoutCoordinates {}
    override val isPlaced: Boolean = true
    override val parentInfo: androidx.compose.ui.layout.LayoutInfo?
        get() = parent
    override val density: androidx.compose.ui.unit.Density = androidx.compose.ui.unit.Density(1f)
    override val layoutDirection: androidx.compose.ui.unit.LayoutDirection =
        androidx.compose.ui.unit.LayoutDirection.Ltr
    override val viewConfiguration: androidx.compose.ui.platform.ViewConfiguration =
        object : androidx.compose.ui.platform.ViewConfiguration {
            override val longPressTimeoutMillis: Long = 500
            override val doubleTapTimeoutMillis: Long = 300
            override val doubleTapMinTimeMillis: Long = 40
            override val touchSlop: Float = 8f
        }
    override val isAttached: Boolean = true
    override val semanticsId: Int = 0
    override fun getModifierInfo(): List<androidx.compose.ui.layout.ModifierInfo> = emptyList()

    internal fun invalidateSubtree() { /* phase 1 no-op */ }
    internal fun invalidateMeasurementForSubtree() { /* phase 1 no-op */ }
    internal fun invalidateDrawForSubtree() { /* phase 1 no-op */ }
    internal fun invalidateParentData() { /* phase 2 no-op */ }
    internal fun requestAutofill() { /* phase 1 no-op */ }

    /** Phase 2 surface: read by CompositionLocalConsumerModifierNode.currentValueOf. */
    internal val compositionLocalMap: androidx.compose.runtime.CompositionLocalMap =
        androidx.compose.runtime.CompositionLocalMap.Empty

    // ============
    //  Geometry (pixels, post-layout)
    var x = 0; private set
    var y = 0; private set
    override var width = 0; private set
    override var height = 0; private set
    /* Previous (w, h) snapshot for change detection; -1 forces fire on first measure. */
    private var lastWidth = -1
    private var lastHeight = -1

    // ============
    //  Content for leaf nodes
    var text: String? = null
    var textColor: Color = Color.White
    /* Optional per-character colour spans over `text` (an AnnotatedString's
       spanStyles). null = single colour (textColor). Color-only as far as layout
       goes: renderers paint each span's range in its colour (gaps fall back to
       textColor), but measurement / wrap use the plain text, so a text field's
       cursor & selection math is unaffected. */
    var textSpans: List<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>>? = null
    /* Pixel font size — Composables that take a TextUnit param resolve it to an
       integer pixel count before storing here so the renderer never sees TextUnit. */
    var fontSize: Int = 16
    var textAlign: TextAlign = TextAlign.Start
    /* Whether text auto-wraps at constraints.maxWidth. BasicTextField sets
       this false on its inner BasicText to keep cursor mapping deterministic. */
    var softWrap: Boolean = true
    /* Font family for the text leaf. null = the renderer's default font
       (bundled Roboto). Non-null = an IconFont entry registered by an icon
       module — both renderers cache typefaces per (family, size). */
    var fontFamily: String? = null
    /* Variable-font axis settings for the text leaf (Material Symbols
       wght/FILL/GRAD/opsz, or any custom axis the bundled font supports).
       null or empty = default axis values. Currently honored by the Skia
       renderer; SDL3_ttf ignores them (no axis-set API in 3.2). */
    var fontVariationSettings: List<androidx.compose.ui.text.font.FontVariation>? = null

    // ============
    //  Cached wrap + measured size for the text leaf. A large static body
    //  (e.g. a 13k-line response) would otherwise be re-wrapped — splitting on
    //  '\n' and allocating one substring per line — on every measure AND every
    //  draw, every frame. We cache the WrappedText keyed by the inputs that
    //  affect wrapping, identity-comparing `text` so an unchanged body reuses
    //  the result and measure + draw share one wrap. Content width is computed
    //  lazily (textContentWidth) only when the node isn't a fixed/fill width,
    //  so the common fillMaxWidth body never measures all N lines.
    private var fWrapCache: com.compose.desktop.native.text.WrappedText? = null
    private var fWrapText: String? = null
    private var fWrapMaxW = -1
    private var fWrapSize = -1
    private var fWrapFamily: String? = null
    private var fWrapTab = -1
    private var fWrapVars: List<androidx.compose.ui.text.font.FontVariation>? = null
    private var fContentW = -1
    /* Measured pixel height of the wrapped text (line count × line height). */
    var textMeasuredHeight = 0; private set

    /* Wrap `text` to inMaxWidth, caching the result so an unchanged text leaf
       isn't re-wrapped every frame. Also fixes textMeasuredHeight. Both the
       measure policy and the renderers call this so they share one wrap. */
    fun layoutText(inMaxWidth: Int): com.compose.desktop.native.text.WrappedText {
        val vText = text ?: ""
        val vTab = com.compose.desktop.native.TextLayoutConfig.tabWidth
        val vCached = fWrapCache
        if (vCached != null && fWrapText === vText && fWrapMaxW == inMaxWidth &&
            fWrapSize == fontSize && fWrapFamily == fontFamily && fWrapTab == vTab &&
            fWrapVars == fontVariationSettings) {
            return vCached
        }
        val vM = com.compose.desktop.native.text.currentTextMeasurer
        val vWrap = vM.wrap(vText, fontSize, inMaxWidth, fontFamily, fontVariationSettings)
        val vLh = vM.lineHeight(fontSize, fontFamily, fontVariationSettings)
        fWrapCache = vWrap
        fWrapText = vText; fWrapMaxW = inMaxWidth; fWrapSize = fontSize
        fWrapFamily = fontFamily; fWrapTab = vTab; fWrapVars = fontVariationSettings
        fContentW = -1
        textMeasuredHeight = (vLh * vWrap.lines.size.coerceAtLeast(1)).toInt()
        return vWrap
    }

    /* The wrap from the most recent layoutText — what the measure pass
       (TextMeasurePolicy, which runs every frame before draw) computed. The
       renderers draw from THIS instead of calling layoutText again with a
       different width: a soft-wrap body is wrapped at its container width by
       measure but its node width settles to the (narrower) content width, so a
       draw-time layoutText(width) used a different cache key and re-wrapped —
       plus reset + rescanned textContentWidth — every single frame. For a huge
       body that alone dropped a static screen from 75 to ~18 fps. Falls back to
       a fresh wrap only before the first measure. */
    fun cachedWrap(): com.compose.desktop.native.text.WrappedText {
        fWrapCache?.let { return it }
        return layoutText(if (softWrap && width > 0) width else Int.MAX_VALUE)
    }

    /* Widest wrapped line in pixels — the text's intrinsic content width.
       Computed lazily off the cached wrap (so a fillMaxWidth body, which
       ignores this, never pays for N per-line measures) and cached until the
       next layoutText recompute. */
    fun textContentWidth(): Int {
        if (fContentW >= 0) return fContentW
        val vWrap = fWrapCache ?: return 0
        val vM = com.compose.desktop.native.text.currentTextMeasurer
        var vMax = 0
        for (vLine in vWrap.lines) {
            val vw = vM.measure(vLine, fWrapSize, Int.MAX_VALUE, fWrapFamily, fWrapVars).width
            if (vw > vMax) vMax = vw
        }
        fContentW = vMax
        return vMax
    }

    // ============
    //  Content for image leaf nodes (set by the Image composable). The renderer
    //  reads painter (resource path + kind) to resolve a cached decoded
    //  texture, then paints it into the node bounds per contentScale / alpha.
    var painter: androidx.compose.ui.graphics.painter.Painter? = null
    var contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit
    var imageAlpha: Float = 1f

    // ============
    //  Content for Canvas{} leaf nodes. The renderer invokes the lambda with
    //  a backend-specific DrawScope as the node's paint operation. Modifier
    //  .drawBehind {} uses a separate path — see DrawBehindModifier — which
    //  paints BEFORE children but AFTER background / border on the same node.
    var drawer: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null

    // ============
    //  Computed absolute position (incl. visual offset modifiers + parent scroll)
    val offsetX: Int get() = cachedOffsetX
    val offsetY: Int get() = cachedOffsetY
    /** ScrollState.value mutates inside the same modifier instance, so the value read goes live every frame; only the state reference is cached. */
    val scrollOffsetX: Int get() = cachedHorizontalScrollState?.value ?: 0
    val scrollOffsetY: Int get() = cachedVerticalScrollState?.value ?: 0
    /** Node-wide opacity (product of `Modifier.alpha` + every `graphicsLayer`'s alpha). */
    val nodeAlpha: Float get() = cachedNodeAlpha
    /** Sum of all `Modifier.zIndex(...)` values on this node; renderer sorts siblings by it. */
    val zIndex: Float get() = cachedZIndex
    /** Last `GraphicsLayerModifier` in the chain, or null. Renderer applies its transform + caching. */
    val graphicsLayer: com.compose.desktop.native.element.GraphicsLayerModifier? get() = cachedGraphicsLayer
    /* Parent's scroll offset shifts this node's visual position; the node's
       own scroll offset applies to its children but not to itself. */
    val absoluteX: Int get() {
        val p = parent ?: return x + offsetX
        return x + offsetX + p.absoluteX - p.scrollOffsetX
    }
    val absoluteY: Int get() {
        val p = parent ?: return y + offsetY
        return y + offsetY + p.absoluteY - p.scrollOffsetY
    }

    // ============
    //  Tree manipulation

    fun insertAt(index: Int, child: LayoutNode) {
        child.parent = this
        children.add(index, child)
    }

    fun removeAt(index: Int, count: Int) {
        repeat(count) {
            val removed = children.removeAt(index)
            removed.parent = null
        }
    }

    fun move(from: Int, to: Int, count: Int) {
        val moved = (0 until count).map { children.removeAt(from) }
        val dest = if (to > from) to - count else to
        children.addAll(dest, moved)
    }

    // ============
    //  Layout

    /* Guards against recursive Modifier.layout interception when the
       user's onMeasure body calls measurable.measure(): the second pass
       must run the NATURAL measure without re-applying the modifier. */
    private var fSkipLayoutModifier: Boolean = false

    fun measure(constraints: Constraints): IntSize {
        // Modifier.layout intercept — runs once, then the user's onMeasure
        // body calls measurable.measure(c) which re-enters measure() with
        // fSkipLayoutModifier=true to take the natural path.
        if (!fSkipLayoutModifier) {
            val vLayoutMod = cachedLayoutModifier
            if (vLayoutMod != null) {
                val vMeasurable = object : androidx.compose.ui.layout.Measurable {
                    override val parentData: Any? = null
                    override fun measure(constraints: androidx.compose.ui.unit.Constraints): androidx.compose.ui.layout.Placeable {
                        fSkipLayoutModifier = true
                        try { this@LayoutNode.measure(constraints) }
                        finally { fSkipLayoutModifier = false }
                        return androidx.compose.ui.layout.LayoutNodePlaceable(this@LayoutNode)
                    }
                    override fun minIntrinsicWidth(height: Int): Int = 0
                    override fun maxIntrinsicWidth(height: Int): Int = 0
                    override fun minIntrinsicHeight(width: Int): Int = 0
                    override fun maxIntrinsicHeight(width: Int): Int = 0
                }
                val vScope = androidx.compose.ui.layout.MeasureScopeImpl()
                val vResult = vLayoutMod.onMeasure.invoke(vScope, vMeasurable, constraints)
                vResult.placeChildren()
                width = vResult.width
                height = vResult.height
                return IntSize(width, height)
            }
        }
        val adjusted = applyModifierConstraints(constraints)

        // Scroll modifiers: children measured with unbounded length in the
        // scroll axis, own bounds clamp to incoming maxLength.
        val vScrollV = cachedVerticalScrollState
        val vScrollH = cachedHorizontalScrollState
        val childConstraints = Constraints(
            minWidth = adjusted.minWidth,
            maxWidth = if (vScrollH != null) Constraints.Infinity else adjusted.maxWidth,
            minHeight = adjusted.minHeight,
            maxHeight = if (vScrollV != null) Constraints.Infinity else adjusted.maxHeight,
        )

        val result = measurePolicy.measure(this, childConstraints)

        val vCapHeight = if (vScrollV != null && adjusted.maxHeight != Constraints.Infinity) {
            vScrollV.setMaxInternal((result.height - adjusted.maxHeight).coerceAtLeast(0), adjusted.maxHeight)
            adjusted.maxHeight
        } else result.height

        val vCapWidth = if (vScrollH != null && adjusted.maxWidth != Constraints.Infinity) {
            vScrollH.setMaxInternal((result.width - adjusted.maxWidth).coerceAtLeast(0), adjusted.maxWidth)
            adjusted.maxWidth
        } else result.width

        width = vCapWidth
        height = vCapHeight

        // Fire MeasuredSizeAwareModifierNode.onRemeasured() on every node in
        // the chain when size actually changes — that's the upstream-shape
        // entry point that the vendored `Modifier.onSizeChanged { … }`
        // (in androidx.compose.ui.layout.OnRemeasuredModifier.kt) wires its
        // callback through. State writes inside land in the global snapshot
        // and recompose on the next frame, so this is safe to call from measure.
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            val vSize = IntSize(width, height)
            var vN: androidx.compose.ui.Modifier.Node? = nodes.head.child
            while (vN != null) {
                if (vN is androidx.compose.ui.node.MeasuredSizeAwareModifierNode) vN.onRemeasured(vSize)
                vN = vN.child
            }
        }

        return IntSize(width, height)
    }


    fun place(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /* Previous absolute position; -1 = first dispatch, force a fire. */
    private var lastAbsX = Int.MIN_VALUE
    private var lastAbsY = Int.MIN_VALUE

    /* Walks the tree after place() and fires every GloballyPositionedModifier
       whose node's absolute position has changed since last frame. Position
       is computed via absoluteX/Y, so this MUST run after all places resolve
       — call it from ComposeWindow's loop, not from inside place itself.

       Also dispatches `LayoutAwareModifierNode.onPlaced(coordinates)` on
       every node in the Modifier.Node chain — that's the upstream-shape
       entry point `Modifier.onPlaced { … }` wires its callback through. */
    fun dispatchGloballyPositioned() {
        val vAx = absoluteX
        val vAy = absoluteY
        if (vAx != lastAbsX || vAy != lastAbsY) {
            lastAbsX = vAx
            lastAbsY = vAy
            var vN: androidx.compose.ui.Modifier.Node? = nodes.head.child
            while (vN != null) {
                if (vN is androidx.compose.ui.node.LayoutAwareModifierNode) {
                    vN.onPlaced(coordinator.coordinates)
                }
                vN = vN.child
            }
            for (g in cachedGloballyPositionedList) g.onChange(IntOffset(vAx, vAy))
        }
        for (child in children) child.dispatchGloballyPositioned()
    }

    private fun applyModifierConstraints(incoming: Constraints): Constraints {
        if (cachedSizes.isEmpty()) return incoming
        var c = incoming
        for (size in cachedSizes) {
            var minW = c.minWidth; var maxW = c.maxWidth
            var minH = c.minHeight; var maxH = c.maxHeight

            if (size.width >= 0) { minW = size.width; maxW = size.width }
            if (size.height >= 0) { minH = size.height; maxH = size.height }
            // fillMax* is a no-op when the incoming max on that axis is
            // unbounded (e.g. inside a scroll measured at Infinity) — matches
            // official Compose. Without the guard the node would size to
            // Int.MAX_VALUE, overflowing downstream math.
            if (size.fillMaxWidth && incoming.maxWidth != Constraints.Infinity) { minW = incoming.maxWidth; maxW = incoming.maxWidth }
            if (size.fillMaxHeight && incoming.maxHeight != Constraints.Infinity) { minH = incoming.maxHeight; maxH = incoming.maxHeight }
            if (size.minWidth >= 0) {
                // defaultMinSize: only apply if nothing upstream pinned the min.
                if (!size.isDefaultMin || minW == 0) minW = max(minW, size.minWidth)
            }
            if (size.minHeight >= 0) {
                if (!size.isDefaultMin || minH == 0) minH = max(minH, size.minHeight)
            }
            if (size.maxWidth >= 0) maxW = min(maxW, size.maxWidth)
            if (size.maxHeight >= 0) maxH = min(maxH, size.maxHeight)

            c = Constraints(
                minWidth = minW.coerceAtMost(maxW),
                maxWidth = maxW.coerceAtLeast(minW),
                minHeight = minH.coerceAtMost(maxH),
                maxHeight = maxH.coerceAtLeast(minH),
            )
        }
        return c
    }

    // ============
    //  Padding helpers

    val paddingLeft: Int get() = cachedPaddingLeft
    val paddingTop: Int get() = cachedPaddingTop
    val paddingRight: Int get() = cachedPaddingRight
    val paddingBottom: Int get() = cachedPaddingBottom

    // ============
    //  Hit testing

    fun hitTest(px: Int, py: Int): LayoutNode? {
        val ax = absoluteX; val ay = absoluteY
        if (px < ax || py < ay || px >= ax + width || py >= ay + height) return null
        for (i in children.indices.reversed()) {
            val hit = children[i].hitTest(px, py)
            if (hit != null) return hit
        }
        return this
    }

    fun findClickHandler(): (() -> Unit)? = cachedClickable?.onClick ?: parent?.findClickHandler()
    fun findSecondaryClickHandler(): ((Int, Int) -> Unit)? = cachedSecondaryClickable?.onClick ?: parent?.findSecondaryClickHandler()
    fun findMiddleClickHandler(): (() -> Unit)? = cachedMiddleClickable?.onClick ?: parent?.findMiddleClickHandler()

    /** Self → root walk collecting every HoverableModifier with its owning node. */
    fun collectHoverableChain(): List<Pair<LayoutNode, HoverableModifier>> {
        val acc = mutableListOf<Pair<LayoutNode, HoverableModifier>>()
        var n: LayoutNode? = this
        while (n != null) {
            for (h in n.cachedHoverables) acc.add(n!! to h)
            n = n.parent
        }
        return acc
    }

    /** First PressableModifier on the self → root walk. */
    fun findPressable(): Pair<LayoutNode, PressableModifier>? {
        var n: LayoutNode? = this
        while (n != null) {
            val p = n.cachedPressable
            if (p != null) return n to p
            n = n.parent
        }
        return null
    }

    /** First clickable node on the self → root walk — release-inside-press check (Compose's drag-off-cancels-click). */
    fun findClickableNode(): LayoutNode? {
        var n: LayoutNode? = this
        while (n != null) {
            if (n.cachedClickable != null) return n
            n = n.parent
        }
        return null
    }

    /** First node on the self → root walk that carries an OnDragModifier. */
    fun findDraggable(): Pair<LayoutNode, OnDragModifier>? {
        var n: LayoutNode? = this
        while (n != null) {
            val d = n.cachedDraggable
            if (d != null) return n to d
            n = n.parent
        }
        return null
    }

    /** First node on the self → root walk that carries a FocusableModifier. */
    fun findFocusableNode(): Pair<LayoutNode, FocusableModifier>? {
        var n: LayoutNode? = this
        while (n != null) {
            val f = n.cachedFocusable
            if (f != null) return n to f
            n = n.parent
        }
        return null
    }

    /** First vertical-scroll ancestor (or self). */
    fun findVerticalScrollAncestor(): androidx.compose.foundation.ScrollState? {
        var n: LayoutNode? = this
        while (n != null) {
            val s = n.cachedVerticalScrollState
            if (s != null) return s
            n = n.parent
        }
        return null
    }

    /** First horizontal-scroll ancestor (or self). */
    fun findHorizontalScrollAncestor(): androidx.compose.foundation.ScrollState? {
        var n: LayoutNode? = this
        while (n != null) {
            val s = n.cachedHorizontalScrollState
            if (s != null) return s
            n = n.parent
        }
        return null
    }

    /**
     * Dispatch a key event up the chain. The first OnKeyEventModifier that
     * returns true consumes; otherwise the event keeps bubbling. Multiple
     * handlers per node are tried in chain order before falling through to
     * the parent.
     */
    fun dispatchKeyEvent(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
        var n: LayoutNode? = this
        while (n != null) {
            for (h in n.cachedKeyEventHandlers) if (h(keyEvent)) return true
            n = n.parent
        }
        return false
    }

    /**
     * Dispatch IME-committed text up the chain. The first OnTextInputModifier
     * receives it; later handlers don't. (Unlike key events, text input
     * doesn't have a "return false to bubble" — once a focused field has
     * claimed it, ancestors shouldn't double-insert.)
     */
    fun dispatchTextInput(text: String) {
        var n: LayoutNode? = this
        while (n != null) {
            val h = n.cachedTextInputHandler
            if (h != null) { h(text); return }
            n = n.parent
        }
    }
}

// ==================
// MARK: MeasurePolicy
// ==================

// Internal node-level measure policy — distinct from (and kept off the public
// ABI to avoid colliding with) the official androidx.compose.ui.layout.MeasurePolicy.
internal fun interface MeasurePolicy {
    fun measure(node: LayoutNode, constraints: Constraints): IntSize
}

// Default: wrap children stacked at (0,0)
internal val DefaultMeasurePolicy = MeasurePolicy { node, constraints ->
    var maxW = 0; var maxH = 0
    for (child in node.children) {
        val childSize = child.measure(constraints)
        child.place(0, 0)
        maxW = max(maxW, childSize.width)
        maxH = max(maxH, childSize.height)
    }
    constraints.constrain(IntSize(maxW, maxH))
}
