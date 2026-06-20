package androidx.compose.ui.node

import androidx.compose.ui.*
import androidx.compose.ui.HoverableModifier
import androidx.compose.ui.PressableModifier
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

    // ============
    //  Content for leaf nodes
    var text: String? = null
    var textColor: Color = Color.White
    /* Pixel font size — Composables that take a Sp param resolve it to an
       integer pixel count before storing here so the renderer never sees Sp. */
    var fontSize: Int = 16
    var textAlign: TextAlign = TextAlign.Start

    // ============
    //  Computed absolute position (incl. visual offset modifiers)
    val offsetX: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is OffsetModifier) acc + e.x else acc
    }
    val offsetY: Int get() = modifier.foldIn(0) { acc, e ->
        if (e is OffsetModifier) acc + e.y else acc
    }
    val absoluteX: Int get() = x + offsetX + (parent?.absoluteX ?: 0)
    val absoluteY: Int get() = y + offsetY + (parent?.absoluteY ?: 0)

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
        val result = measurePolicy.measure(this, adjusted)
        width = result.width
        height = result.height
        return result
    }

    fun place(x: Int, y: Int) {
        this.x = x
        this.y = y
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
