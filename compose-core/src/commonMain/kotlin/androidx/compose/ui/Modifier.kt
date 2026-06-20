package androidx.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

// ==================
// MARK: Modifier
// ==================

interface Modifier {

    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R
    fun <R> foldOut(initial: R, operation: (Element, R) -> R): R
    fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R) = operation(initial, this)
        override fun <R> foldOut(initial: R, operation: (Element, R) -> R) = operation(this, initial)
    }

    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R) = initial
        override fun <R> foldOut(initial: R, operation: (Element, R) -> R) = initial
        override fun then(other: Modifier) = other
        override fun toString() = "Modifier"
    }
}

// ==================
// MARK: CombinedModifier
// ==================

private class CombinedModifier(val outer: Modifier, val inner: Modifier) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R =
        outer.foldOut(inner.foldOut(initial, operation), operation)

    override fun toString() = "[${outer}, ${inner}]"
}

// ==================
// MARK: Modifier Elements (internal)
// ==================

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
// `state` here is the foundation-package ScrollState exposed via Any so this
// file doesn't pull in a foundation dependency; LayoutNode reads it via the
// public ScrollState API in the foundation package.

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
