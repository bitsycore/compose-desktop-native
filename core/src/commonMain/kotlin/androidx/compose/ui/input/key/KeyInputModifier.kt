package androidx.compose.ui.input.key

import androidx.compose.ui.KeyEventDispatch
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnKeyEventModifier

// ==================
// MARK: Modifier.onKeyEvent
// ==================

/* Receives raw key events while the node (or a focusable descendant) is
   focused. Return true to consume the event; false bubbles to the next
   handler up the focus chain. NOTE: official onKeyEvent takes (KeyEvent) ->
   Boolean; this passes the project's KeyEventDispatch wrapper (see CLAUDE.md
   known-diverging). */
fun Modifier.onKeyEvent(onKeyEvent: (KeyEventDispatch) -> Boolean) =
	then(OnKeyEventModifier(onKeyEvent))
