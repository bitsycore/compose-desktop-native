package com.compose.desktop.native.element

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement

// ==================
// MARK: Modifier Elements
// ==================
// Each modifier is a `ModifierNodeElement<XxxNode>` paired with a
// `XxxNode : Modifier.Node, DrawModifierNode` (or other *ModifierNode
// interface). LayoutNode and the renderers read these via Modifier.foldIn
// (ModifierNodeElement IS-A Modifier.Element), so the migration to the
// upstream factory pattern is transparent to the renderer today — the
// Node lifecycle stays dormant until the renderer rewrite drives it via
// NodeCoordinator. Equality is hand-written to match data-class semantics.

// `PaddingModifier` + `PaddingNode` retired — replaced by vendored
// upstream `androidx.compose.foundation.layout.Padding.kt`. The chain
// measure pipeline runs PaddingNode.measure() outermost-first;
// `placeable.place(start, top)` inside its layout block accumulates
// into `LayoutNode.contentOffsetX/Y` via `ChainLeafPlaceable.placeAt`.


class BackgroundModifier(
    val color: Color,
    val shape: Shape = RectangleShape,
) : ModifierNodeElement<BackgroundNode>() {
    override fun create() = BackgroundNode(color, shape)
    override fun update(node: BackgroundNode) { node.color = color; node.shape = shape }
    override fun hashCode(): Int = 31 * color.hashCode() + shape.hashCode()
    override fun equals(other: Any?): Boolean =
        other is BackgroundModifier && other.color == color && other.shape == shape
}

class BackgroundNode(var color: Color, var shape: Shape) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() { drawContent() /* dormant — renderer reads BackgroundModifier via foldIn */ }
}

class BorderModifier(
    val width: Int,
    val color: Color,
    val shape: Shape = RectangleShape,
) : ModifierNodeElement<BorderNode>() {
    override fun create() = BorderNode(width, color, shape)
    override fun update(node: BorderNode) { node.width = width; node.color = color; node.shape = shape }
    override fun hashCode(): Int = (31 * width + color.hashCode()) * 31 + shape.hashCode()
    override fun equals(other: Any?): Boolean =
        other is BorderModifier && other.width == width && other.color == color && other.shape == shape
}

class BorderNode(var width: Int, var color: Color, var shape: Shape) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() { drawContent() /* dormant */ }
}

// SizeModifier / SizeNode retired — all size / fill / wrapContent / widthIn /
// heightIn / sizeIn / required* / defaultMinSize behaviour comes from upstream
// vendored `androidx.compose.foundation.layout.Size.kt`, whose private
// SizeNode / FillNode / WrapContentNode / UnspecifiedConstraintsNode classes
// participate in the LayoutModifierNode chain.

class ClickableModifier(val onClick: () -> Unit) : ModifierNodeElement<ClickableNode>() {
    override fun create() = ClickableNode(onClick)
    override fun update(node: ClickableNode) { node.onClick = onClick }
    override fun hashCode(): Int = onClick.hashCode()
    override fun equals(other: Any?): Boolean = other is ClickableModifier && other.onClick === onClick
}
class ClickableNode(var onClick: () -> Unit) : Modifier.Node()

class SecondaryClickModifier(val onClick: (x: Int, y: Int) -> Unit) : ModifierNodeElement<SecondaryClickNode>() {
    override fun create() = SecondaryClickNode(onClick)
    override fun update(node: SecondaryClickNode) { node.onClick = onClick }
    override fun hashCode(): Int = onClick.hashCode()
    override fun equals(other: Any?): Boolean = other is SecondaryClickModifier && other.onClick === onClick
}
class SecondaryClickNode(var onClick: (x: Int, y: Int) -> Unit) : Modifier.Node()

class MiddleClickModifier(val onClick: () -> Unit) : ModifierNodeElement<MiddleClickNode>() {
    override fun create() = MiddleClickNode(onClick)
    override fun update(node: MiddleClickNode) { node.onClick = onClick }
    override fun hashCode(): Int = onClick.hashCode()
    override fun equals(other: Any?): Boolean = other is MiddleClickModifier && other.onClick === onClick
}
class MiddleClickNode(var onClick: () -> Unit) : Modifier.Node()

/* Fires after the place() pass with the node's absolute window-coordinate
   position. Different from OnSizeChangedModifier in that the callback runs
   even when only the position (not size) changes — useful for popups that
   need to anchor to a moving target. Identity by callback reference. */
class GloballyPositionedModifier(
    val onChange: (androidx.compose.ui.unit.IntOffset) -> Unit,
) : ModifierNodeElement<GloballyPositionedNode>() {
    override fun create() = GloballyPositionedNode(onChange)
    override fun update(node: GloballyPositionedNode) { node.onChange = onChange }
    override fun hashCode(): Int = onChange.hashCode()
    override fun equals(other: Any?): Boolean = other is GloballyPositionedModifier && other.onChange === onChange
}
class GloballyPositionedNode(var onChange: (androidx.compose.ui.unit.IntOffset) -> Unit) : Modifier.Node()

/* Paints user-supplied content under the node's children via a DrawScope
   lambda. The renderer invokes onDraw after background / border and before
   painting children, with the DrawScope sized to the node's bounds.
   Multiple drawBehinds on the same node compose in modifier order — the
   later one paints on top. */
class DrawBehindModifier(
    val onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) : ModifierNodeElement<DrawBehindNode>() {
    override fun create() = DrawBehindNode(onDraw)
    override fun update(node: DrawBehindNode) { node.onDraw = onDraw }
    override fun hashCode(): Int = onDraw.hashCode()
    override fun equals(other: Any?): Boolean = other is DrawBehindModifier && other.onDraw === onDraw
}
class DrawBehindNode(
    var onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() { drawContent() /* dormant */ }
}

/* Identity is by callback reference — not great across recomposition. The
   dispatch code in ComposeWindow keys hover/press state by LayoutNode
   identity (which is stable) rather than by the modifier itself. */
class HoverableModifier(val onChange: (Boolean) -> Unit) : ModifierNodeElement<HoverableNode>() {
    override fun create() = HoverableNode(onChange)
    override fun update(node: HoverableNode) { node.onChange = onChange }
    override fun hashCode(): Int = onChange.hashCode()
    override fun equals(other: Any?): Boolean = other is HoverableModifier && other.onChange === onChange
}
class HoverableNode(var onChange: (Boolean) -> Unit) : Modifier.Node()

class PressableModifier(val onChange: (Boolean) -> Unit) : ModifierNodeElement<PressableNode>() {
    override fun create() = PressableNode(onChange)
    override fun update(node: PressableNode) { node.onChange = onChange }
    override fun hashCode(): Int = onChange.hashCode()
    override fun equals(other: Any?): Boolean = other is PressableModifier && other.onChange === onChange
}
class PressableNode(var onChange: (Boolean) -> Unit) : Modifier.Node()

// OffsetModifier / OffsetNode retired — `Modifier.offset(...)` and
// `Modifier.absoluteOffset(...)` come from upstream vendored
// `androidx.compose.foundation.layout.Offset.kt`, whose private OffsetNode /
// OffsetPxNode classes participate in the LayoutModifierNode chain.

// ==================
// MARK: Focus + keyboard input
// ==================

/* Marks the node as a focus target. */
class FocusableModifier(val onFocusChanged: (Boolean) -> Unit) : ModifierNodeElement<FocusableNode>() {
    override fun create() = FocusableNode(onFocusChanged)
    override fun update(node: FocusableNode) { node.onFocusChanged = onFocusChanged }
    override fun hashCode(): Int = onFocusChanged.hashCode()
    override fun equals(other: Any?): Boolean = other is FocusableModifier && other.onFocusChanged === onFocusChanged
}
class FocusableNode(var onFocusChanged: (Boolean) -> Unit) : Modifier.Node()

// `OnKeyEventModifier` + `OnKeyEventNode` were retired when
// `androidx.compose.ui.input.key.KeyInputModifier.kt` got vendored —
// the vendored file ships the official-shape `Modifier.onKeyEvent {}`
// and `Modifier.onPreviewKeyEvent {}` extensions + a `private
// KeyInputElement` element + `internal KeyInputNode : KeyInputModifierNode`.
// `LayoutNode.dispatchKeyEvent` walks the Modifier.Node chain for
// every `KeyInputModifierNode` and calls its `onKeyEvent` method.


class OnTextInputModifier(val handler: (String) -> Unit) : ModifierNodeElement<OnTextInputNode>() {
    override fun create() = OnTextInputNode(handler)
    override fun update(node: OnTextInputNode) { node.handler = handler }
    override fun hashCode(): Int = handler.hashCode()
    override fun equals(other: Any?): Boolean = other is OnTextInputModifier && other.handler === handler
}
class OnTextInputNode(var handler: (String) -> Unit) : Modifier.Node()

class OnPressedModifier(val handler: (relX: Int, relY: Int) -> Unit) : ModifierNodeElement<OnPressedNode>() {
    override fun create() = OnPressedNode(handler)
    override fun update(node: OnPressedNode) { node.handler = handler }
    override fun hashCode(): Int = handler.hashCode()
    override fun equals(other: Any?): Boolean = other is OnPressedModifier && other.handler === handler
}
class OnPressedNode(var handler: (relX: Int, relY: Int) -> Unit) : Modifier.Node()

class OnDragModifier(
    val onStart: (relX: Int, relY: Int) -> Unit,
    val onDrag: (relX: Int, relY: Int) -> Unit,
    val onEnd: () -> Unit,
) : ModifierNodeElement<OnDragNode>() {
    override fun create() = OnDragNode(onStart, onDrag, onEnd)
    override fun update(node: OnDragNode) { node.onStart = onStart; node.onDrag = onDrag; node.onEnd = onEnd }
    override fun hashCode(): Int = (onStart.hashCode() * 31 + onDrag.hashCode()) * 31 + onEnd.hashCode()
    override fun equals(other: Any?): Boolean =
        other is OnDragModifier && other.onStart === onStart && other.onDrag === onDrag && other.onEnd === onEnd
}
class OnDragNode(
    var onStart: (relX: Int, relY: Int) -> Unit,
    var onDrag: (relX: Int, relY: Int) -> Unit,
    var onEnd: () -> Unit,
) : Modifier.Node()

// `OnSizeChangedModifier` + `OnSizeChangedNode` were retired when
// `androidx.compose.ui.layout.OnRemeasuredModifier.kt` got vendored —
// the vendored file ships the official-shape pair (a `private
// OnSizeChangedModifier` element + `internal OnSizeChangedNode :
// Modifier.Node, MeasuredSizeAwareModifierNode`). The renderer reads
// the new node via `nodes` chain in `LayoutNode.measure()`.


// ==================
// MARK: Scroll
// ==================

class VerticalScrollModifier(
    val state: androidx.compose.foundation.ScrollState,
) : ModifierNodeElement<VerticalScrollNode>() {
    override fun create() = VerticalScrollNode(state)
    override fun update(node: VerticalScrollNode) { node.state = state }
    override fun hashCode(): Int = state.hashCode()
    override fun equals(other: Any?): Boolean = other is VerticalScrollModifier && other.state === state
}
class VerticalScrollNode(var state: androidx.compose.foundation.ScrollState) : Modifier.Node()

class HorizontalScrollModifier(
    val state: androidx.compose.foundation.ScrollState,
) : ModifierNodeElement<HorizontalScrollNode>() {
    override fun create() = HorizontalScrollNode(state)
    override fun update(node: HorizontalScrollNode) { node.state = state }
    override fun hashCode(): Int = state.hashCode()
    override fun equals(other: Any?): Boolean = other is HorizontalScrollModifier && other.state === state
}
class HorizontalScrollNode(var state: androidx.compose.foundation.ScrollState) : Modifier.Node()

class ClipModifier(val shape: Shape) : ModifierNodeElement<ClipNode>() {
    override fun create() = ClipNode(shape)
    override fun update(node: ClipNode) { node.shape = shape }
    override fun hashCode(): Int = shape.hashCode()
    override fun equals(other: Any?): Boolean = other is ClipModifier && other.shape == shape
}
class ClipNode(var shape: Shape) : Modifier.Node()

class LayoutWeightModifier(val weight: Float, val fill: Boolean) : ModifierNodeElement<LayoutWeightNode>() {
    override fun create() = LayoutWeightNode(weight, fill)
    override fun update(node: LayoutWeightNode) { node.weight = weight; node.fill = fill }
    override fun hashCode(): Int = weight.hashCode() * 31 + fill.hashCode()
    override fun equals(other: Any?): Boolean = other is LayoutWeightModifier && other.weight == weight && other.fill == fill
}
class LayoutWeightNode(var weight: Float, var fill: Boolean) : Modifier.Node()

class AlphaModifier(val alpha: Float) : ModifierNodeElement<AlphaNode>() {
    override fun create() = AlphaNode(alpha)
    override fun update(node: AlphaNode) { node.alpha = alpha }
    override fun hashCode(): Int = alpha.hashCode()
    override fun equals(other: Any?): Boolean = other is AlphaModifier && other.alpha == alpha
}
class AlphaNode(var alpha: Float) : Modifier.Node()

// ==================
// MARK: GraphicsLayerModifier
// ==================

/**
 * A "graphics layer" element: alpha + 2D transform (scale / rotation /
 * translation), with an optional cacheKey that opts the subtree into
 * render-to-texture caching across frames. See `Modifier.graphicsLayer`
 * (in `androidx.compose.ui.graphics`) for the caching semantics.
 *
 * The renderer reads this element directly via the `LayoutNode.graphicsLayer`
 * `foldIn` over the chain; the paired [GraphicsLayerNode] lifecycle stays
 * dormant until the renderer rewrite drives it.
 */
class GraphicsLayerModifier(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationZ: Float = 0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
    val cacheKey: Any? = null,
) : ModifierNodeElement<GraphicsLayerNode>() {

    val needsLayer: Boolean
        get() = alpha < 1f || cacheKey != null

    val needsTransform: Boolean
        get() = scaleX != 1f || scaleY != 1f || rotationZ != 0f ||
            translationX != 0f || translationY != 0f

    val isIdentity: Boolean
        get() = !needsLayer && !needsTransform

    override fun create() =
        GraphicsLayerNode(alpha, scaleX, scaleY, rotationZ, translationX, translationY, transformOrigin, cacheKey)
    override fun update(node: GraphicsLayerNode) {
        node.alpha = alpha; node.scaleX = scaleX; node.scaleY = scaleY
        node.rotationZ = rotationZ; node.translationX = translationX; node.translationY = translationY
        node.transformOrigin = transformOrigin; node.cacheKey = cacheKey
    }
    override fun hashCode(): Int {
        var v = alpha.hashCode()
        v = 31 * v + scaleX.hashCode(); v = 31 * v + scaleY.hashCode()
        v = 31 * v + rotationZ.hashCode()
        v = 31 * v + translationX.hashCode(); v = 31 * v + translationY.hashCode()
        v = 31 * v + transformOrigin.hashCode(); v = 31 * v + (cacheKey?.hashCode() ?: 0)
        return v
    }
    override fun equals(other: Any?): Boolean =
        other is GraphicsLayerModifier &&
            other.alpha == alpha && other.scaleX == scaleX && other.scaleY == scaleY &&
            other.rotationZ == rotationZ &&
            other.translationX == translationX && other.translationY == translationY &&
            other.transformOrigin == transformOrigin && other.cacheKey == cacheKey
}

class GraphicsLayerNode(
    var alpha: Float,
    var scaleX: Float,
    var scaleY: Float,
    var rotationZ: Float,
    var translationX: Float,
    var translationY: Float,
    var transformOrigin: TransformOrigin,
    var cacheKey: Any?,
) : Modifier.Node()
