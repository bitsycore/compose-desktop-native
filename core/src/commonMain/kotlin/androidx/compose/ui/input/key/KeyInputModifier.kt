package androidx.compose.ui.input.key

import androidx.compose.ui.Modifier
import com.compose.desktop.native.element.OnKeyEventModifier

// ==================
// MARK: Modifier.onKeyEvent
// ==================

/* Receives raw key events while the node (or a focusable descendant) is
   focused. Return true to consume the event; false bubbles to the next
   handler up the focus chain. Matches official Compose's
   `Modifier.onKeyEvent((KeyEvent) -> Boolean)`. */
fun Modifier.onKeyEvent(onKeyEvent: (KeyEvent) -> Boolean) =
	then(OnKeyEventModifier(onKeyEvent))
