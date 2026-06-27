package androidx.compose.ui.node

import androidx.compose.ui.*
import com.compose.desktop.native.element.*
import com.compose.desktop.native.element.FocusableModifier
import com.compose.desktop.native.element.GloballyPositionedModifier
import androidx.compose.ui.unit.IntOffset
import com.compose.desktop.native.element.HorizontalScrollModifier
import com.compose.desktop.native.element.HoverableModifier
import com.compose.desktop.native.element.KeyEventDispatch
import com.compose.desktop.native.element.OnDragModifier
import com.compose.desktop.native.element.OnKeyEventModifier
import com.compose.desktop.native.element.OnSizeChangedModifier
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

class LayoutNode {
    var parent: LayoutNode? = null
    val children = mutableListOf<LayoutNode>()

    var modifier: Modifier = Modifier
    internal var measurePolicy: MeasurePolicy = DefaultMeasurePolicy

    // ============
    //  Geometry (pixels, post-layout)
    var x = 0; private set
    var y = 0; private set
    var width = 0; private set
    var height = 0; private set
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
    var textSpans: List<androidx.compose.ui.text.Range<androidx.compose.ui.text.SpanStyle>>? = null
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
    private var fWrapCache: androidx.compose.ui.text.WrappedText? = null
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
    fun layoutText(inMaxWidth: Int): androidx.compose.ui.text.WrappedText {
        val vText = text ?: ""
        val vTab = com.compose.desktop.native.TextLayoutConfig.tabWidth
        val vCached = fWrapCache
        if (vCached != null && fWrapText === vText && fWrapMaxW == inMaxWidth &&
            fWrapSize == fontSize && fWrapFamily == fontFamily && fWrapTab == vTab &&
            fWrapVars == fontVariationSettings) {
            return vCached
        }
        val vM = androidx.compose.ui.text.currentTextMeasurer
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
    fun cachedWrap(): androidx.compose.ui.text.WrappedText {
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
        val vM = androidx.compose.ui.text.currentTextMeasurer
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
    val offsetX: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is OffsetModifier) acc + e.x else acc
    }
    val offsetY: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is OffsetModifier) acc + e.y else acc
    }
    val scrollOffsetX: Int get() {
        var v = 0
        modifier.foldIn(Unit) { _, e -> if (e is HorizontalScrollModifier) v += e.state.value }
        return v
    }
    val scrollOffsetY: Int get() {
        var v = 0
        modifier.foldIn(Unit) { _, e -> if (e is VerticalScrollModifier) v += e.state.value }
        return v
    }
    /* Node-wide opacity from Modifier.alpha and Modifier.graphicsLayer
       (product of all alpha contributions); 1f = fully opaque. The
       renderer composites the subtree at this alpha. */
    val nodeAlpha: Float get() = modifier.foldIn(1f) { acc, e ->
        when (e) {
            is AlphaModifier                                                -> acc * e.alpha
            is com.compose.desktop.native.element.GraphicsLayerModifier           -> acc * e.alpha
            else                                                            -> acc
        }
    }
    /* Sum of all Modifier.zIndex(...) values applied to this node. Used
       by the renderer to order siblings within their parent — higher z
       draws on top. Defaults to 0 when no zIndex modifier is present. */
    val zIndex: Float get() {
        var v = 0f
        modifier.foldIn(Unit) { _, e ->
            if (e is androidx.compose.ui.ZIndexModifier) v += e.zIndex
        }
        return v
    }
    /* The (last) GraphicsLayerModifier in this node's chain, or null if
       none. Used by renderers to apply transforms and per-node caching. */
    val graphicsLayer: com.compose.desktop.native.element.GraphicsLayerModifier? get() {
        var v: com.compose.desktop.native.element.GraphicsLayerModifier? = null
        modifier.foldIn(Unit) { _, e ->
            if (e is com.compose.desktop.native.element.GraphicsLayerModifier) v = e
        }
        return v
    }
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
            val vLayoutMod = findLayoutModifier()
            if (vLayoutMod != null) {
                val vMeasurable = object : androidx.compose.ui.layout.Measurable {
                    override fun measure(constraints: androidx.compose.ui.unit.Constraints): androidx.compose.ui.layout.Placeable {
                        fSkipLayoutModifier = true
                        try { this@LayoutNode.measure(constraints) }
                        finally { fSkipLayoutModifier = false }
                        return androidx.compose.ui.layout.LayoutNodePlaceable(this@LayoutNode)
                    }
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
        val vScrollV = findVerticalScroll()
        val vScrollH = findHorizontalScroll()
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

        // Fire OnSizeChangedModifier listeners when size actually changes.
        // State writes inside the callback land in the global snapshot and
        // recompose on the next frame, so this is safe to call from measure.
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            val vSize = IntSize(width, height)
            modifier.foldIn(Unit) { _, e ->
                if (e is OnSizeChangedModifier) e.onChange(vSize)
            }
        }

        return IntSize(width, height)
    }

    private fun findVerticalScroll(): androidx.compose.foundation.ScrollState? {
        var s: androidx.compose.foundation.ScrollState? = null
        modifier.foldIn(Unit) { _, e -> if (e is VerticalScrollModifier && s == null) s = e.state }
        return s
    }

    private fun findHorizontalScroll(): androidx.compose.foundation.ScrollState? {
        var s: androidx.compose.foundation.ScrollState? = null
        modifier.foldIn(Unit) { _, e -> if (e is HorizontalScrollModifier && s == null) s = e.state }
        return s
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
       — call it from ComposeWindow's loop, not from inside place itself. */
    fun dispatchGloballyPositioned() {
        val vAx = absoluteX
        val vAy = absoluteY
        if (vAx != lastAbsX || vAy != lastAbsY) {
            lastAbsX = vAx
            lastAbsY = vAy
            modifier.foldIn(Unit) { _, e ->
                if (e is GloballyPositionedModifier) {
                    e.onChange(IntOffset(vAx, vAy))
                }
            }
        }
        for (vChild in children) vChild.dispatchGloballyPositioned()
    }

    private fun applyModifierConstraints(incoming: Constraints): Constraints {
        var c = incoming
        modifier.foldIn(Unit) { _, element ->
            if (element is SizeModifier) {
                var minW = c.minWidth; var maxW = c.maxWidth
                var minH = c.minHeight; var maxH = c.maxHeight

                if (element.width >= 0) { minW = element.width; maxW = element.width }
                if (element.height >= 0) { minH = element.height; maxH = element.height }
                // fillMax* is a no-op when the incoming max on that axis is
                // unbounded (e.g. inside a scroll measured at Infinity) — matches
                // official Compose. Without the guard the node would size to
                // Int.MAX_VALUE, overflowing downstream math (e.g. the renderer's
                // off-screen cull `x + width`), which blanked a no-wrap body
                // inside a horizontalScroll.
                if (element.fillMaxWidth && incoming.maxWidth != Constraints.Infinity) { minW = incoming.maxWidth; maxW = incoming.maxWidth }
                if (element.fillMaxHeight && incoming.maxHeight != Constraints.Infinity) { minH = incoming.maxHeight; maxH = incoming.maxHeight }
                if (element.minWidth >= 0) {
                    // defaultMinSize: only apply if nothing upstream pinned the min.
                    if (!element.isDefaultMin || minW == 0) minW = max(minW, element.minWidth)
                }
                if (element.minHeight >= 0) {
                    if (!element.isDefaultMin || minH == 0) minH = max(minH, element.minHeight)
                }
                if (element.maxWidth >= 0) maxW = min(maxW, element.maxWidth)
                if (element.maxHeight >= 0) maxH = min(maxH, element.maxHeight)

                c = Constraints(
                    minWidth = minW.coerceAtMost(maxW),
                    maxWidth = maxW.coerceAtLeast(minW),
                    minHeight = minH.coerceAtMost(maxH),
                    maxHeight = maxH.coerceAtLeast(minH)
                )
            }
        }
        return c
    }

    // ============
    //  Padding helpers

    val paddingLeft: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is PaddingModifier) acc + e.start else acc
    }
    val paddingTop: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is PaddingModifier) acc + e.top else acc
    }
    val paddingRight: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is PaddingModifier) acc + e.end else acc
    }
    val paddingBottom: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is PaddingModifier) acc + e.bottom else acc
    }

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

    fun findClickHandler(): (() -> Unit)? {
        var handler: (() -> Unit)? = null
        modifier.foldIn(Unit) { _, element ->
            if (element is ClickableModifier) handler = element.onClick
        }
        if (handler != null) return handler
        return parent?.findClickHandler()
    }

    fun findSecondaryClickHandler(): ((Int, Int) -> Unit)? {
        var handler: ((Int, Int) -> Unit)? = null
        modifier.foldIn(Unit) { _, element ->
            if (element is SecondaryClickModifier) handler = element.onClick
        }
        if (handler != null) return handler
        return parent?.findSecondaryClickHandler()
    }

    fun findMiddleClickHandler(): (() -> Unit)? {
        var handler: (() -> Unit)? = null
        modifier.foldIn(Unit) { _, element ->
            if (element is MiddleClickModifier) handler = element.onClick
        }
        if (handler != null) return handler
        return parent?.findMiddleClickHandler()
    }

    /* Walks self → root and collects nodes that carry a HoverableModifier.
       The matching modifier is returned alongside the node so the dispatcher
       can compute the enter/exit diff and fire callbacks. */
    fun collectHoverableChain(): List<Pair<LayoutNode, HoverableModifier>> {
        val acc = mutableListOf<Pair<LayoutNode, HoverableModifier>>()
        var n: LayoutNode? = this
        while (n != null) {
            val current = n
            current.modifier.foldIn(Unit) { _, e ->
                if (e is HoverableModifier) acc.add(current to e)
            }
            n = current.parent
        }
        return acc
    }

    /* First PressableModifier on the self → root walk. */
    fun findPressable(): Pair<LayoutNode, PressableModifier>? {
        var n: LayoutNode? = this
        while (n != null) {
            var hit: PressableModifier? = null
            val current = n
            current.modifier.foldIn(Unit) { _, e ->
                if (e is PressableModifier && hit == null) hit = e
            }
            val found = hit
            if (found != null) return current to found
            n = current.parent
        }
        return null
    }

    /* First clickable node on the self → root walk — used by ComposeWindow to
       verify that release lands inside the same clickable that received the
       press (Compose's drag-off-cancels-click semantics). */
    fun findClickableNode(): LayoutNode? {
        var n: LayoutNode? = this
        while (n != null) {
            var has = false
            n.modifier.foldIn(Unit) { _, e -> if (e is ClickableModifier) has = true }
            if (has) return n
            n = n.parent
        }
        return null
    }

    /* First scroll modifier on the self → root walk, with its node. Used by
       ComposeWindow's wheel dispatch to find what to scroll. */
    /* Looks for a Modifier.layout(...) on this node only — the modifier
       wraps THIS node's measure, it doesn't propagate to ancestors. */
    private fun findLayoutModifier(): androidx.compose.ui.layout.LayoutModifierElement? {
        var v: androidx.compose.ui.layout.LayoutModifierElement? = null
        modifier.foldIn(Unit) { _, e ->
            if (e is androidx.compose.ui.layout.LayoutModifierElement) v = e
        }
        return v
    }

    fun findVerticalScrollAncestor(): androidx.compose.foundation.ScrollState? {
        var n: LayoutNode? = this
        while (n != null) {
            val v = n.findVerticalScroll()
            if (v != null) return v
            n = n.parent
        }
        return null
    }

    fun findHorizontalScrollAncestor(): androidx.compose.foundation.ScrollState? {
        var n: LayoutNode? = this
        while (n != null) {
            val v = n.findHorizontalScroll()
            if (v != null) return v
            n = n.parent
        }
        return null
    }

    /* First node on the self → root walk that carries an OnDragModifier.
       Used by ComposeWindow to capture drag gestures. */
    fun findDraggable(): Pair<LayoutNode, OnDragModifier>? {
        var n: LayoutNode? = this
        while (n != null) {
            val current = n
            var hit: OnDragModifier? = null
            current.modifier.foldIn(Unit) { _, e ->
                if (e is OnDragModifier && hit == null) hit = e
            }
            val found = hit
            if (found != null) return current to found
            n = current.parent
        }
        return null
    }

    /* First node on the self → root walk that carries a FocusableModifier. */
    fun findFocusableNode(): Pair<LayoutNode, FocusableModifier>? {
        var n: LayoutNode? = this
        while (n != null) {
            val current = n
            var hit: FocusableModifier? = null
            current.modifier.foldIn(Unit) { _, e ->
                if (e is FocusableModifier && hit == null) hit = e
            }
            val found = hit
            if (found != null) return current to found
            n = current.parent
        }
        return null
    }

    /* Dispatch a key event up the chain (this node + its ancestors). The
       first OnKeyEventModifier that returns true consumes; otherwise the
       event keeps bubbling. */
    fun dispatchKeyEvent(inDispatch: KeyEventDispatch): Boolean {
        var n: LayoutNode? = this
        while (n != null) {
            val current = n
            var consumed = false
            current.modifier.foldIn(Unit) { _, e ->
                if (!consumed && e is OnKeyEventModifier) {
                    if (e.handler(inDispatch)) consumed = true
                }
            }
            if (consumed) return true
            n = current.parent
        }
        return false
    }

    /* Dispatch IME-committed text up the chain. The first OnTextInputModifier
       receives it; later handlers don't. (Unlike key events, text input
       doesn't have a "return false to bubble" — once a focused field has
       claimed it, ancestors shouldn't double-insert.) */
    fun dispatchTextInput(inText: String) {
        var n: LayoutNode? = this
        while (n != null) {
            val current = n
            var handler: ((String) -> Unit)? = null
            current.modifier.foldIn(Unit) { _, e ->
                if (handler == null && e is OnTextInputModifier) handler = e.handler
            }
            val found = handler
            if (found != null) {
                found(inText)
                return
            }
            n = current.parent
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
