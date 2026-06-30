package com.compose.desktop.native.node

import androidx.compose.ui.*
import com.compose.desktop.native.element.*
import com.compose.desktop.native.element.FocusableModifier
import androidx.compose.ui.unit.IntOffset
import com.compose.desktop.native.element.HorizontalScrollModifier
import com.compose.desktop.native.element.HoverableModifier
import com.compose.desktop.native.element.OnDragModifier
import com.compose.desktop.native.element.OnTextInputModifier
import com.compose.desktop.native.element.OnPressedModifier
import com.compose.desktop.native.element.PressableModifier
import com.compose.desktop.native.element.VerticalScrollModifier
import com.compose.desktop.native.input.PointerInputElement
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
    // Padding / Offset caches retired — `Modifier.padding`, `Modifier.offset`,
    // `Modifier.absoluteOffset` all flow through vendored upstream
    // `Padding.kt` / `Offset.kt` + the LayoutModifierNode chain pipeline
    // (contentOffsetX/Y set by `ChainLeafPlaceable.placeAt` during the
    // chain's deferred `placeChildren` walk inside `place(x, y)`).
    private var cachedZIndex: Float = 0f
    private var cachedNodeAlpha: Float = 1f
    private var cachedGraphicsLayer: com.compose.desktop.native.element.GraphicsLayerModifier? = null
    // cachedSizes retired — `Modifier.size / width / height / fillMax* /
    // wrapContent* / widthIn / heightIn / sizeIn / requiredSize /
    // defaultMinSize` flow through the LayoutModifierNode chain (vendored
    // upstream `Size.kt`).
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
    /** First FocusableModifier on the chain — `:window`'s focusManager reads it to invoke onFocusChanged. */
    var cachedFocusable: FocusableModifier? = null
        private set
    // Key dispatch is now sourced from upstream `KeyInputModifierNode` nodes — see
    // `dispatchKeyEvent` below; it walks the Modifier.Node chain instead of a cached list.
    private var cachedTextInputHandler: ((String) -> Unit)? = null
    // cachedGloballyPositionedList retired — `Modifier.onGloballyPositioned` is
    // now provided by vendored upstream `OnGloballyPositionedModifier.kt`
    // and dispatched by walking the Modifier.Node chain for
    // `GlobalPositionAwareModifierNode` instances in `dispatchGloballyPositioned`.

    // Render-side caches (read by SkiaRenderer + Sdl3Renderer's per-frame draw loops).
    /** Every BackgroundModifier in chain order — renderer paints each on the node's bounds. */
    var cachedBackgrounds: List<BackgroundModifier> = emptyList()
        private set
    /** Every BorderModifier in chain order. */
    var cachedBorders: List<BorderModifier> = emptyList()
        private set
    /** Every DrawBehindModifier in chain order. */
    var cachedDrawBehinds: List<com.compose.desktop.native.element.DrawBehindModifier> = emptyList()
        private set
    /** True if the node clips its children (ClipModifier OR a scroll modifier). */
    var clipsChildren: Boolean = false
        private set
    /** Shape used to clip children: ClipModifier.shape if present, else `RectangleShape` for a scroll viewport, else null. */
    var childClipShape: androidx.compose.ui.graphics.Shape? = null
        private set
    /** Last LayoutWeightModifier on the chain — Row/Column read `.weight` + `.fill` during measure. */
    var cachedLayoutWeight: LayoutWeightModifier? = null
        private set

    /**
     * Every [androidx.compose.ui.node.LayoutModifierNode] in the chain,
     * head→tail order. The layout pass invokes each one's `measure(...)`
     * outermost-first, wrapping the next inner modifier (and finally the
     * project's natural measure on children) inside a [Measurable]. This is
     * how upstream's per-modifier coordinator chain dispatches —
     * `Modifier.padding(8.dp).size(100.dp)` produces a 2-element list and
     * each contributes its measure() effect.
     */
    private var cachedLayoutModifierNodes: List<androidx.compose.ui.node.LayoutModifierNode> = emptyList()

    /**
     * Accumulated placement offset from the LayoutModifierNode chain — when
     * `Modifier.padding(8.dp)` wraps a node, the chain's placeAt call lands
     * (8, 8) here and the renderer / absolute-position math adds it to
     * every child's coordinate. Set inside the chain's `placeChildren`
     * walk triggered by `place(x, y)`.
     *
     * Mirrors what upstream's per-modifier `NodeCoordinator` graph does
     * implicitly via separate coordinator positions; we collapse that
     * into a single `(contentOffsetX, contentOffsetY)` on the single
     * LayoutNode.
     */
    var contentOffsetX: Int = 0
        private set
    var contentOffsetY: Int = 0
        private set

    /** Outermost MeasureResult from the chain measure pipeline — its `placeChildren()` runs at `place(x, y)` time to propagate offsets inward. */
    private var pendingChainResult: androidx.compose.ui.layout.MeasureResult? = null

    // Window-side caches (read by :window's ComposeWindow event loop).
    /** Every FocusRequesterModifier on the node — `bindFocusRequesters` pops each onto its host. */
    var cachedFocusRequesters: List<androidx.compose.ui.focus.FocusRequesterModifier> = emptyList()
        private set
    /** Every PointerInputElement — the dispatch loop delivers events to each scope. */
    var cachedPointerInputs: List<PointerInputElement> = emptyList()
        private set
    /** Every OnPressedModifier — positional press dispatch fires each. */
    var cachedOnPressedHandlers: List<(relX: Int, relY: Int) -> Unit> = emptyList()
        private set

    private fun recomputeChainCaches() {
        var zIdx = 0f
        var alpha = 1f
        var gl: com.compose.desktop.native.element.GraphicsLayerModifier? = null
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
        var text: ((String) -> Unit)? = null
        var backgrounds: MutableList<BackgroundModifier>? = null
        var borders: MutableList<BorderModifier>? = null
        var drawBehinds: MutableList<com.compose.desktop.native.element.DrawBehindModifier>? = null
        var clipShape: androidx.compose.ui.graphics.Shape? = null
        var hasScrollClip = false
        var weight: LayoutWeightModifier? = null
        var focusRequesters: MutableList<androidx.compose.ui.focus.FocusRequesterModifier>? = null
        var pointerInputs: MutableList<PointerInputElement>? = null
        var onPressedHandlers: MutableList<(Int, Int) -> Unit>? = null
        modifier.foldIn(Unit) { _, e ->
            when (e) {
                is androidx.compose.ui.ZIndexElement                                -> zIdx += e.zIndex
                is AlphaModifier                                                    -> alpha *= e.alpha
                is com.compose.desktop.native.element.GraphicsLayerModifier         -> { alpha *= e.alpha; gl = e }
                is androidx.compose.ui.layout.LayoutModifierElement                 -> layoutMod = e
                is HorizontalScrollModifier                                         -> { if (hScroll == null) hScroll = e.state; hasScrollClip = true }
                is VerticalScrollModifier                                           -> { if (vScroll == null) vScroll = e.state; hasScrollClip = true }
                is ClickableModifier                                                -> click = e
                is SecondaryClickModifier                                           -> click2 = e
                is MiddleClickModifier                                              -> click3 = e
                is HoverableModifier                                                -> { (hovers ?: mutableListOf<HoverableModifier>().also { hovers = it }).add(e) }
                is PressableModifier                                                -> { if (press == null) press = e }
                is OnDragModifier                                                   -> { if (drag == null) drag = e }
                is FocusableModifier                                                -> { if (focus == null) focus = e }
                is OnTextInputModifier                                              -> { if (text == null) text = e.handler }
                is BackgroundModifier                                               -> { (backgrounds ?: mutableListOf<BackgroundModifier>().also { backgrounds = it }).add(e) }
                is BorderModifier                                                   -> { (borders ?: mutableListOf<BorderModifier>().also { borders = it }).add(e) }
                is com.compose.desktop.native.element.DrawBehindModifier            -> { (drawBehinds ?: mutableListOf<com.compose.desktop.native.element.DrawBehindModifier>().also { drawBehinds = it }).add(e) }
                is ClipModifier                                                     -> clipShape = e.shape
                is LayoutWeightModifier                                             -> weight = e
                is androidx.compose.ui.focus.FocusRequesterModifier                 -> { (focusRequesters ?: mutableListOf<androidx.compose.ui.focus.FocusRequesterModifier>().also { focusRequesters = it }).add(e) }
                is PointerInputElement                                              -> { (pointerInputs ?: mutableListOf<PointerInputElement>().also { pointerInputs = it }).add(e) }
                is OnPressedModifier                                                -> { (onPressedHandlers ?: mutableListOf<(Int, Int) -> Unit>().also { onPressedHandlers = it }).add(e.handler) }
            }
        }
        cachedZIndex = zIdx
        cachedNodeAlpha = alpha
        cachedGraphicsLayer = gl
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
        cachedTextInputHandler = text
        cachedBackgrounds = backgrounds ?: emptyList()
        cachedBorders = borders ?: emptyList()
        cachedDrawBehinds = drawBehinds ?: emptyList()
        clipsChildren = clipShape != null || hasScrollClip
        // Renderer rule: ClipModifier.shape wins; else if any scroll, use RectangleShape; else null.
        childClipShape = clipShape ?: if (hasScrollClip) androidx.compose.ui.graphics.RectangleShape else null
        cachedLayoutWeight = weight
        cachedFocusRequesters = focusRequesters ?: emptyList()
        cachedPointerInputs = pointerInputs ?: emptyList()
        cachedOnPressedHandlers = onPressedHandlers ?: emptyList()

        // Collect LayoutModifierNodes from the chain in head→tail order so the
        // measure pipeline can apply them outermost-first.
        var lmnList: MutableList<androidx.compose.ui.node.LayoutModifierNode>? = null
        var n: androidx.compose.ui.Modifier.Node? = nodes.head.child
        while (n != null) {
            if (n is androidx.compose.ui.node.LayoutModifierNode) {
                (lmnList ?: mutableListOf<androidx.compose.ui.node.LayoutModifierNode>().also { lmnList = it }).add(n)
            }
            n = n.child
        }
        cachedLayoutModifierNodes = lmnList ?: emptyList()
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
    /**
     * LayoutCoordinates for this node. Returns the node's absolute window
     * position (logical points) from `positionInWindow()` so vendored
     * `GlobalPositionAwareModifierNode.onGloballyPositioned(coordinates)`
     * + `Modifier.onGloballyPositioned { … }` receivers can read it.
     */
    override val coordinates: androidx.compose.ui.layout.LayoutCoordinates =
        object : androidx.compose.ui.layout.LayoutCoordinates {
            override val size: androidx.compose.ui.unit.IntSize
                get() = androidx.compose.ui.unit.IntSize(width, height)
            override val isAttached: Boolean get() = true
            override fun positionInWindow(): androidx.compose.ui.geometry.Offset =
                androidx.compose.ui.geometry.Offset(absoluteX.toFloat(), absoluteY.toFloat())
        }
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

    /** Phase 2 surface: read by CompositionLocalConsumerModifierNode.currentValueOf.
        Now `var` (was `val`) so vendored `CompositionLocalMapInjectionNode`
        in ComposedModifier.kt can write to it during onAttach + map updates. */
    internal var compositionLocalMap: androidx.compose.runtime.CompositionLocalMap =
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
    //  Computed absolute position (incl. parent scroll + chain content offset)
    // offsetX/Y retired — `Modifier.offset` is now a LayoutModifierNode whose
    // place(x, y) effect flows through the chain into the parent's
    // contentOffsetX/Y (we sit at (x, y) inside our parent's content space).
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
        val p = parent ?: return x
        return x + p.absoluteX + p.contentOffsetX - p.scrollOffsetX
    }
    val absoluteY: Int get() {
        val p = parent ?: return y
        return y + p.absoluteY + p.contentOffsetY - p.scrollOffsetY
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

    /**
     * Apply `cachedLayoutModifierNodes` outermost-first to [constraints].
     *
     * Builds a chain of [Measurable] wrappers: the innermost calls back
     * into `measure()` with the layout-modifier-chain skipped (so the
     * project's natural `applyModifierConstraints` + `measurePolicy`
     * path runs); each outer wrapper invokes its node's `measure(...)`
     * with the next inner wrapped as the [Measurable]. Returns a
     * [Placeable] reporting the outermost result's size; the result's
     * `placeChildren()` is also invoked so any place-with-offset effect
     * (Padding's `placeable.place(left, top)`) lands.
     */
    private fun measureViaLayoutModifierChain(constraints: Constraints): androidx.compose.ui.layout.Placeable {
        val scope = androidx.compose.ui.layout.MeasureScopeImpl()
        val outerNode = this

        // Innermost wrapper — re-enters `measure(c)` with the layout-modifier
        // chain guard set so the natural path runs (applyModifierConstraints +
        // measurePolicy on children). The returned [ChainLeafPlaceable]'s
        // placeAt(x, y) accumulates (x, y) into LayoutNode.contentOffset.
        val leaf = object : androidx.compose.ui.layout.Measurable {
            override val parentData: Any? = null
            override fun measure(c: androidx.compose.ui.unit.Constraints): androidx.compose.ui.layout.Placeable {
                val saved = fSkipLayoutModifier
                fSkipLayoutModifier = true
                try { this@LayoutNode.measure(c) } finally { fSkipLayoutModifier = saved }
                return ChainLeafPlaceable(this@LayoutNode)
            }
            override fun minIntrinsicWidth(height: Int): Int = 0
            override fun maxIntrinsicWidth(height: Int): Int = 0
            override fun minIntrinsicHeight(width: Int): Int = 0
            override fun maxIntrinsicHeight(width: Int): Int = 0
        }

        // Wrap each LayoutModifierNode around the next-inner Measurable.
        // Iterate reverse so the outermost (chain head) ends up at the top.
        var current: androidx.compose.ui.layout.Measurable = leaf
        for (i in cachedLayoutModifierNodes.indices.reversed()) {
            val lmn = cachedLayoutModifierNodes[i]
            val inner = current
            current = object : androidx.compose.ui.layout.Measurable {
                override val parentData: Any? = null
                override fun measure(c: androidx.compose.ui.unit.Constraints): androidx.compose.ui.layout.Placeable {
                    val result = with(lmn) { scope.measure(inner, c) }
                    return ChainStepPlaceable(result, outerNode)
                }
                override fun minIntrinsicWidth(height: Int): Int = 0
                override fun maxIntrinsicWidth(height: Int): Int = 0
                override fun minIntrinsicHeight(width: Int): Int = 0
                override fun maxIntrinsicHeight(width: Int): Int = 0
            }
        }

        val outerPlaceable = current.measure(constraints)
        // Defer the outermost result's placeChildren until `place(x, y)` runs
        // — at that point contentOffset is reset and the chain walk lands
        // the per-step offsets back on this LayoutNode.
        pendingChainResult = if (outerPlaceable is ChainStepPlaceable) outerPlaceable.result else null
        return outerPlaceable
    }

    /**
     * Wrap-step Placeable: every LayoutModifierNode in the chain produces
     * one (size from the node's MeasureResult). `placeAt(x, y)` adds
     * (x, y) to the LayoutNode's contentOffset and triggers the wrapped
     * result's [placeChildren], which propagates to the next inner step.
     */
    private class ChainStepPlaceable(
        val result: androidx.compose.ui.layout.MeasureResult,
        val node: LayoutNode,
    ) : androidx.compose.ui.layout.Placeable() {
        override val width: Int = result.width
        override val height: Int = result.height
        override fun placeAt(inX: Int, inY: Int) {
            node.contentOffsetX += inX
            node.contentOffsetY += inY
            result.placeChildren()
        }
    }

    /**
     * Leaf Placeable for the chain — wraps the LayoutNode's natural measure.
     * `placeAt(x, y)` accumulates into contentOffset (the natural measure
     * has already set width/height on the LayoutNode).
     */
    private class ChainLeafPlaceable(val node: LayoutNode) : androidx.compose.ui.layout.Placeable() {
        override val width: Int get() = node.width
        override val height: Int get() = node.height
        override fun placeAt(inX: Int, inY: Int) {
            node.contentOffsetX += inX
            node.contentOffsetY += inY
        }
    }

    fun measure(constraints: Constraints): IntSize {
        // LayoutModifierNode chain — head→tail, each one's `measure()` wraps
        // the next inner modifier (and finally the natural measure on
        // children) inside a Measurable. This is how upstream Padding /
        // Size / Offset all do their work; pulling it through here is
        // what makes vendoring those upstream files possible.
        if (!fSkipLayoutModifier && cachedLayoutModifierNodes.isNotEmpty()) {
            val placeable = measureViaLayoutModifierChain(constraints)
            width = placeable.width
            height = placeable.height
            return IntSize(width, height)
        }
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
        // Constraints reach us already constrained by the LayoutModifierNode
        // chain (vendored Padding / Size / Offset run via the chain pipeline
        // ahead of measure). What's left here is the scroll intercept.
        val vScrollV = cachedVerticalScrollState
        val vScrollH = cachedHorizontalScrollState
        val childConstraints = Constraints(
            minWidth = constraints.minWidth,
            maxWidth = if (vScrollH != null) Constraints.Infinity else constraints.maxWidth,
            minHeight = constraints.minHeight,
            maxHeight = if (vScrollV != null) Constraints.Infinity else constraints.maxHeight,
        )

        val result = measurePolicy.measure(this, childConstraints)

        val vCapHeight = if (vScrollV != null && constraints.maxHeight != Constraints.Infinity) {
            vScrollV.setMaxInternal((result.height - constraints.maxHeight).coerceAtLeast(0), constraints.maxHeight)
            constraints.maxHeight
        } else result.height

        val vCapWidth = if (vScrollH != null && constraints.maxWidth != Constraints.Infinity) {
            vScrollH.setMaxInternal((result.width - constraints.maxWidth).coerceAtLeast(0), constraints.maxWidth)
            constraints.maxWidth
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
        // Reset and replay the LayoutModifierNode chain's placement so each
        // wrapping layout block's `placeable.place(dx, dy)` accumulates into
        // contentOffsetX/Y. Children then render at (parent.contentOffsetX +
        // child.x, ...) via absoluteX/Y.
        contentOffsetX = 0
        contentOffsetY = 0
        pendingChainResult?.placeChildren()
    }

    /* Previous absolute position; -1 = first dispatch, force a fire. */
    private var lastAbsX = Int.MIN_VALUE
    private var lastAbsY = Int.MIN_VALUE

    /* Walks the tree after place() and fires every GloballyPositionedModifier
       whose node's absolute position has changed since last frame. Position
       is computed via absoluteX/Y, so this MUST run after all places resolve
       — call it from ComposeWindow's loop, not from inside place itself.

       Also dispatches `LayoutAwareModifierNode.onPlaced(coordinates)` and
       `GlobalPositionAwareModifierNode.onGloballyPositioned(coordinates)`
       on every node in the Modifier.Node chain — those are the upstream-
       shape entry points `Modifier.onPlaced { … }` and
       `Modifier.onGloballyPositioned { … }` wire their callbacks through. */
    fun dispatchGloballyPositioned() {
        val vAx = absoluteX
        val vAy = absoluteY
        if (vAx != lastAbsX || vAy != lastAbsY) {
            lastAbsX = vAx
            lastAbsY = vAy
            var vN: androidx.compose.ui.Modifier.Node? = nodes.head.child
            while (vN != null) {
                if (vN is androidx.compose.ui.node.LayoutAwareModifierNode) {
                    vN.onPlaced(coordinates)
                }
                if (vN is androidx.compose.ui.node.GlobalPositionAwareModifierNode) {
                    vN.onGloballyPositioned(coordinates)
                }
                vN = vN.child
            }
        }
        for (child in children) child.dispatchGloballyPositioned()
    }

    // applyModifierConstraints retired — all size / fill / wrapContent /
    // widthIn / heightIn / sizeIn / required* / defaultMinSize constraint
    // adjustment now happens in the LayoutModifierNode chain pipeline
    // ahead of the natural measure path.

    // ============
    //  Padding helpers

    // paddingLeft/Top/Right/Bottom retired — Box/Row/Column no longer read them.
    // Padding's effect flows through `contentOffsetX/Y` via the chain measure
    // pipeline (set by ChainLeafPlaceable.placeAt during the chain's deferred
    // placeChildren walk in `place(x, y)`).

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
     * Dispatch a key event up the chain. Walks each ancestor's Modifier.Node
     * chain and calls `KeyInputModifierNode.onKeyEvent` on every match in
     * chain order; the first to return true consumes. Routes through the
     * upstream-shape API that `Modifier.onKeyEvent { }` wires its callback
     * through.
     */
    fun dispatchKeyEvent(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
        var n: LayoutNode? = this
        while (n != null) {
            var node: androidx.compose.ui.Modifier.Node? = n.nodes.head.child
            while (node != null) {
                if (node is androidx.compose.ui.input.key.KeyInputModifierNode && node.onKeyEvent(keyEvent)) return true
                node = node.child
            }
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
