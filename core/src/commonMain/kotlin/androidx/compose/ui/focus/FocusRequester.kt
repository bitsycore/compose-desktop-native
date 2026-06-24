package androidx.compose.ui.focus

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutNode

// ==================
// MARK: FocusRequester
// ==================

/* Handle on a focusable composable, used to call .requestFocus() from
   anywhere in the composition (e.g. from a button click that should
   focus a text field). The Modifier.focusRequester(this) call binds
   this requester to its host node; calling requestFocus() walks back
   to that node and routes through the active FocusManager. */
class FocusRequester {

	/* Bound to the host node by the renderer (window module). Public for
	   cross-module visibility (Kotlin's `internal` is per-module). App
	   code should not poke at these directly — use requestFocus(). */
	var attachedNode: LayoutNode? = null
	var focusManager: FocusManager? = null

	/* Move focus to the bound node. No-op if no node is bound (the
	   modifier hasn't laid out yet) or no FocusManager is installed. */
	fun requestFocus() {
		val vN = attachedNode ?: return
		focusManager?.focusOnNode(vN)
	}

	/* Clear focus from the bound node. No-op if it isn't the currently
	   focused one. */
	fun freeFocus() {
		focusManager?.clearFocus()
	}
}

// ==================
// MARK: FocusManager
// ==================

/* Active focus controller for a composition. Installed by the renderer
   host (:window) at composeWindow setup so user code can move focus
   without reaching into the window event loop. */
interface FocusManager {

	fun focusOnNode(node: LayoutNode)
	fun clearFocus()
}

/* CompositionLocal threading the active FocusManager through the tree.
   Reads as null outside composeWindow. */
val LocalFocusManager = compositionLocalOf<FocusManager?> { null }

// ==================
// MARK: FocusRequesterModifier
// ==================

class FocusRequesterModifier(val focusRequester: FocusRequester) : Modifier.Element

/* Bind a FocusRequester to this node. Pair with Modifier.focusable so
   the node actually accepts focus. */
fun Modifier.focusRequester(focusRequester: FocusRequester): Modifier =
	this.then(FocusRequesterModifier(focusRequester))

// ==================
// MARK: onFocusChanged (standalone)
// ==================

/* Listen to focus changes on this node without making it focusable.
   Useful for composables that bundle their own focus indicator outside
   the focusable's onFocusChanged slot. */
class OnFocusChangedModifier(val onChange: (Boolean) -> Unit) : Modifier.Element

fun Modifier.onFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier =
	this.then(OnFocusChangedModifier(onFocusChanged))
