package com.compose.desktop.native.element

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin

// ==================
// MARK: Modifier Elements (render-bridge — non-official)
// ==================
// The data/holder classes a Modifier chain folds into; LayoutNode and the
// renderers read them via foldIn. None of these exist in official Compose
// (which uses Modifier.Node), so they live in the com.compose.desktop.native
// layer rather than androidx.compose.ui. The official Modifier extensions
// (Modifier.background / .padding / .clip / ...) stay in their androidx
// packages and construct these.

data class PaddingModifier(
    val start: Int = 0,
    val top: Int = 0,
    val end: Int = 0,
    val bottom: Int = 0
) : Modifier.Element

data class BackgroundModifier(val color: Color, val shape: Shape = RectangleShape) : Modifier.Element
data class BorderModifier(val width: Int, val color: Color, val shape: Shape = RectangleShape) : Modifier.Element

data class SizeModifier(
    val width: Int = -1,
    val height: Int = -1,
    val minWidth: Int = -1,
    val minHeight: Int = -1,
    val maxWidth: Int = -1,
    val maxHeight: Int = -1,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    /* defaultMinSize semantics: only raise the constraint's min if the
       incoming min is still 0 (i.e. nothing upstream pinned a size). When
       false, min* fields apply as a hard lower bound (widthIn/heightIn). */
    val isDefaultMin: Boolean = false
) : Modifier.Element

data class ClickableModifier(val onClick: () -> Unit) : Modifier.Element
data class SecondaryClickModifier(val onClick: (x: Int, y: Int) -> Unit) : Modifier.Element
data class MiddleClickModifier(val onClick: () -> Unit) : Modifier.Element

/* Fires after the place() pass with the node's absolute window-coordinate
   position. Different from OnSizeChangedModifier in that the callback runs
   even when only the position (not size) changes — useful for popups that
   need to anchor to a moving target. Identity by callback reference. */
class GloballyPositionedModifier(
    val onChange: (androidx.compose.ui.unit.IntOffset) -> Unit,
) : Modifier.Element

/* Paints user-supplied content under the node's children via a DrawScope
   lambda. The renderer invokes onDraw after background / border and before
   painting children, with the DrawScope sized to the node's bounds.
   Multiple drawBehinds on the same node compose in modifier order — the
   later one paints on top. */
class DrawBehindModifier(
    val onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) : Modifier.Element

/* Identity is by callback reference — not great across recomposition. The
   dispatch code in ComposeWindow keys hover/press state by LayoutNode
   identity (which is stable) rather than by the modifier itself. */
class HoverableModifier(val onChange: (Boolean) -> Unit) : Modifier.Element
class PressableModifier(val onChange: (Boolean) -> Unit) : Modifier.Element

/* Visual offset applied after layout. The node's measured size and the
   parent's placement are unchanged; only the absolute draw position shifts.
   Multiple OffsetModifiers stack additively. */
data class OffsetModifier(val x: Int, val y: Int) : Modifier.Element

// ==================
// MARK: Focus + keyboard input
// ==================

/* Marks the node as a focus target. Click on the node (or any descendant
   not separately focusable) routes focus here. onFocusChanged fires on
   enter / leave. */
class FocusableModifier(val onFocusChanged: (Boolean) -> Unit) : Modifier.Element

/* Receives raw key events while the node (or a descendant) is focused.
   Return true to consume; false lets the event bubble up the focus chain. */
class OnKeyEventModifier(val handler: (KeyEventDispatch) -> Boolean) : Modifier.Element

/* Receives IME-committed text while the node is focused. Use it to insert
   into a TextFieldValue. */
class OnTextInputModifier(val handler: (String) -> Unit) : Modifier.Element

/* Receives positional press events with coordinates relative to this
   node's absolute top-left. Use it when a node needs to react to *where*
   a click happened — e.g. positioning a text cursor under the pointer.
   The handler fires on PointerEventType.Press, BEFORE focus / click. */
class OnPressedModifier(val handler: (relX: Int, relY: Int) -> Unit) : Modifier.Element

/* Drag gesture. ComposeWindow captures this node on Press (so subsequent
   Move events route here regardless of where the cursor wanders) until
   Release, when it fires onEnd. A click without movement still fires
   onStart immediately and onEnd on release — onDrag may not fire at all. */
class OnDragModifier(
    val onStart: (relX: Int, relY: Int) -> Unit,
    val onDrag: (relX: Int, relY: Int) -> Unit,
    val onEnd: () -> Unit,
) : Modifier.Element

/* Fires after the node's measured size changes. Used by TextField to react
   to its own measured width for soft-wrap. The callback runs during layout
   right after measurement — writes to mutableStateOf inside it will trigger
   the standard recompose-on-next-frame path. */
class OnSizeChangedModifier(val onChange: (androidx.compose.ui.unit.IntSize) -> Unit) : Modifier.Element

// ==================
// MARK: Scroll
// ==================

/* Children measured with unbounded length on the scroll axis; the node
   itself clamps to incoming constraints and applies a -state.value
   translation to its children. ScrollState's maxValue is updated each
   layout pass to (content - viewport).clamp(0..). */
class VerticalScrollModifier(val state: androidx.compose.foundation.ScrollState) : Modifier.Element

class HorizontalScrollModifier(val state: androidx.compose.foundation.ScrollState) : Modifier.Element

/* Wrapper around a key event the modifier sees. Wrapping rather than
   re-exporting androidx.compose.ui.input.key.KeyEvent so we don't pin the
   key-event type prematurely. */
class KeyEventDispatch(
    val key: androidx.compose.ui.input.key.KeyEvent,
)

/* Clips this node's children to the given shape. The node's own bg/border
   drawing is not clipped (they already follow the shape via their own
   shape parameter). Children drawn inside are restricted to the shape. */
data class ClipModifier(val shape: Shape) : Modifier.Element

/* Tells the parent Row / Column to give this child a fraction of the
   leftover main-axis space (after unweighted children take their
   intrinsic size). fill = true (the default upstream) forces the child
   to fill its allotted slice; fill = false caps the child at its
   preferred size while still claiming that share. weight must be > 0. */
data class LayoutWeightModifier(val weight: Float, val fill: Boolean) : Modifier.Element

/* Node-wide opacity: the node and its whole subtree are rendered to an
   offscreen layer and composited back at this alpha, so overlapping content
   inside doesn't double-blend. Multiple AlphaModifiers multiply. Applied by
   the renderer (Skia saveLayer / SDL3 render-to-texture). */
data class AlphaModifier(val alpha: Float) : Modifier.Element

// ==================
// MARK: GraphicsLayerModifier
// ==================

/* A "graphics layer" element: alpha + 2D transform (scale / rotation /
   translation), with an optional cacheKey that opts the subtree into
   render-to-texture caching across frames. See Modifier.graphicsLayer (in
   androidx.compose.ui.graphics) for the caching semantics. */
data class GraphicsLayerModifier(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationZ: Float = 0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
    val cacheKey: Any? = null,
) : Modifier.Element {

    val needsLayer: Boolean
        get() = alpha < 1f || cacheKey != null

    val needsTransform: Boolean
        get() = scaleX != 1f || scaleY != 1f || rotationZ != 0f ||
            translationX != 0f || translationY != 0f

    val isIdentity: Boolean
        get() = !needsLayer && !needsTransform
}
