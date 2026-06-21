package androidx.compose.ui.node

import androidx.compose.ui.*
import androidx.compose.ui.FocusableModifier
import androidx.compose.ui.GloballyPositionedModifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.HorizontalScrollModifier
import androidx.compose.ui.HoverableModifier
import androidx.compose.ui.KeyEventDispatch
import androidx.compose.ui.OnDragModifier
import androidx.compose.ui.OnKeyEventModifier
import androidx.compose.ui.OnSizeChangedModifier
import androidx.compose.ui.OnTextInputModifier
import androidx.compose.ui.PressableModifier
import androidx.compose.ui.VerticalScrollModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: LayoutNode
// ==================

class LayoutNode {
    var parent: LayoutNode? = null
    val children = mutableListOf<LayoutNode>()

    var modifier: Modifier = Modifier
    var measurePolicy: MeasurePolicy = DefaultMeasurePolicy

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
    /* Pixel font size — Composables that take a Sp param resolve it to an
       integer pixel count before storing here so the renderer never sees Sp. */
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
    var fontVariationSettings: List<androidx.compose.ui.text.FontVariation>? = null

    // ============
    //  Content for image leaf nodes (set by the Image composable). The renderer
    //  reads painter (resource path + kind) to resolve a cached decoded
    //  texture, then paints it into the node bounds per contentScale / alpha.
    var painter: androidx.compose.ui.graphics.painter.Painter? = null
    var contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit
    var imageAlpha: Float = 1f

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
    /* Node-wide opacity from Modifier.alpha (product of all AlphaModifiers);
       1f = fully opaque. The renderer composites the subtree at this alpha. */
    val nodeAlpha: Float get() = modifier.foldIn(1f) { acc, e ->
        if (e is AlphaModifier) acc * e.alpha else acc
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

    fun measure(constraints: Constraints): IntSize {
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
            vScrollV.setMaxInternal((result.height - adjusted.maxHeight).coerceAtLeast(0))
            adjusted.maxHeight
        } else result.height

        val vCapWidth = if (vScrollH != null && adjusted.maxWidth != Constraints.Infinity) {
            vScrollH.setMaxInternal((result.width - adjusted.maxWidth).coerceAtLeast(0))
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
                if (element.fillMaxWidth) { minW = incoming.maxWidth; maxW = incoming.maxWidth }
                if (element.fillMaxHeight) { minH = incoming.maxHeight; maxH = incoming.maxHeight }
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

fun interface MeasurePolicy {
    fun measure(node: LayoutNode, constraints: Constraints): IntSize
}

// Default: wrap children stacked at (0,0)
val DefaultMeasurePolicy = MeasurePolicy { node, constraints ->
    var maxW = 0; var maxH = 0
    for (child in node.children) {
        val childSize = child.measure(constraints)
        child.place(0, 0)
        maxW = max(maxW, childSize.width)
        maxH = max(maxH, childSize.height)
    }
    constraints.constrain(maxW, maxH)
}
