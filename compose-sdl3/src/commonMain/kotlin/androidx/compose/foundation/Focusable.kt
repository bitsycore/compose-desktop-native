package androidx.compose.foundation

import androidx.compose.ui.FocusableModifier
import androidx.compose.ui.KeyEventDispatch
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnKeyEventModifier
import androidx.compose.ui.OnTextInputModifier

// ==================
// MARK: Focus + keyboard modifiers
// ==================

/* Marks this node as a focus target. The most recently clicked focusable
   ancestor becomes the focused node; clicks elsewhere remove focus. */
fun Modifier.focusable(onFocusChanged: (Boolean) -> Unit = {}) =
    then(FocusableModifier(onFocusChanged))

/* Receives raw key events while the node (or a focusable descendant) is
   focused. Return true to consume the event; false bubbles to the next
   handler up the focus chain. */
fun Modifier.onKeyEvent(handler: (KeyEventDispatch) -> Boolean) =
    then(OnKeyEventModifier(handler))

/* Receives IME-committed text (SDL_EVENT_TEXT_INPUT) while focused. Use
   for the text-insertion path of a TextField. Key events (arrows,
   backspace etc.) come through onKeyEvent. */
fun Modifier.onTextInput(handler: (String) -> Unit) =
    then(OnTextInputModifier(handler))
