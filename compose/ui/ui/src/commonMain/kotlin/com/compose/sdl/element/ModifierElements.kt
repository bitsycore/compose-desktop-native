package com.compose.sdl.element

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.node.ModifierNodeElement

// ==================
// MARK: Project modifier elements
// ==================
// Small set of project-only modifier elements — each pairs a
// `ModifierNodeElement<XxxNode>` with a `XxxNode : Modifier.Node`. Upstream
// LayoutNode reads them through the chain (ModifierNodeElement IS-A
// Modifier.Element). Nothing that has a full upstream equivalent lives here
// anymore — Modifier.background / border / drawBehind / focusable / weight
// / alpha are all vendored. What's left:
//   * SecondaryClick / MiddleClick — non-upstream (upstream Clickable is
//     primary-only).
//   * Pressable / OnTextInput / OnPressed / OnDrag — the project's cheap
//     pointer surface used by ComposeRootHost.onPointer (B6a).
//   * ClipModifier — GraphicsLayer.kt lowers `clip = true` to it (project
//     GraphicsLayerModifier still owns the transform pipeline).

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

class PressableModifier(val onChange: (Boolean) -> Unit) : ModifierNodeElement<PressableNode>() {
    override fun create() = PressableNode(onChange)
    override fun update(node: PressableNode) { node.onChange = onChange }
    override fun hashCode(): Int = onChange.hashCode()
    override fun equals(other: Any?): Boolean = other is PressableModifier && other.onChange === onChange
}
class PressableNode(var onChange: (Boolean) -> Unit) : Modifier.Node()

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

class ClipModifier(val shape: Shape) : ModifierNodeElement<ClipNode>() {
    override fun create() = ClipNode(shape)
    override fun update(node: ClipNode) { node.shape = shape }
    override fun hashCode(): Int = shape.hashCode()
    override fun equals(other: Any?): Boolean = other is ClipModifier && other.shape == shape
}
class ClipNode(var shape: Shape) : Modifier.Node()

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
