package com.compose.sdl.modifier

import com.compose.sdl.element.MiddleClickModifier
import androidx.compose.ui.Modifier
import com.compose.sdl.element.OnDragModifier
import com.compose.sdl.element.OnPressedModifier
import com.compose.sdl.element.OnTextInputModifier
import com.compose.sdl.element.PressableModifier
import com.compose.sdl.element.SecondaryClickModifier

// ==================
// MARK: Non-official Modifier extensions ("layer on top")
// ==================

// These have NO official Compose equivalent, so they live in the project's own
// com.compose.sdl.* namespace rather than androidx.compose.* (which
// is kept a faithful mirror of official Compose). They wrap the render-bridge
// Modifier.Element glue declared in androidx.compose.ui.

/* Fires on a secondary (right) mouse-button click inside this node, with the
   click's absolute window coordinates (logical points) — handy for opening a
   context menu at the cursor. Does not arm the primary click. */
fun Modifier.onSecondaryClick(onClick: (x: Int, y: Int) -> Unit) = then(SecondaryClickModifier(onClick))

/* Fires on a middle (mouse-wheel button) click inside this node. */
fun Modifier.onMiddleClick(onClick: () -> Unit) = then(MiddleClickModifier(onClick))

/* Fires (true) on mouse-press inside this node, (false) on release or when the
   cursor drags off the node while held. Official path: clickable(interactionSource
   = ...).collectIsPressedAsState(). */
fun Modifier.pressable(onChange: (Boolean) -> Unit) = then(PressableModifier(onChange))

/* Receives IME-committed text (SDL_EVENT_TEXT_INPUT) while focused. Used by the
   text-insertion path of a TextField. */
fun Modifier.onTextInput(handler: (String) -> Unit) = then(OnTextInputModifier(handler))

/* Receives positional press events: handler fires with coordinates relative to
   this node's absolute top-left. Used by TextField to place the cursor. */
fun Modifier.onPressed(handler: (relX: Int, relY: Int) -> Unit) = then(OnPressedModifier(handler))

/* Drag gesture: onStart fires on Press inside the node, onDrag on every Move
   (even off-node — the node is captured until Release), onEnd on Release. */
fun Modifier.onDrag(
	onStart: (relX: Int, relY: Int) -> Unit = { _, _ -> },
	onDrag: (relX: Int, relY: Int) -> Unit = { _, _ -> },
	onEnd: () -> Unit = {},
) = then(OnDragModifier(onStart, onDrag, onEnd))

// The rememberMutableInteractionSource() helper moved to :foundation
// (com.compose.sdl.modifier.InteractionSourceHelper.kt) so
// :core doesn't need to depend on foundation. Callers that were importing
// com.compose.sdl.modifier.rememberMutableInteractionSource
// keep the same import — the fully-qualified path is unchanged; it just
// lives in a different Gradle module now.
