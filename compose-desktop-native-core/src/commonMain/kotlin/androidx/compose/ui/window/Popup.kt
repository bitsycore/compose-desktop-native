package androidx.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

// ==================
// MARK: PopupHostState
// ==================

/* Renderer for overlay content (Dialogs, Tooltips, DropdownMenus, Snackbars).
   The composeWindow() entry point installs a single PopupHostState into the
   composition's LocalPopupHost and renders every active entry at the root
   level, *after* the main tree. Children are drawn in registration order, so
   later popups stack visually on top of earlier ones.

   Popups register via the `Popup` composable below. Re-registration on each
   composition keeps the captured-state lambdas fresh; DisposableEffect's
   onDispose runs when the parent removes the popup from the tree, removing
   the entry from the host. */
class PopupHostState internal constructor() {

	internal class Entry(val id: Any, var content: @Composable () -> Unit)

	internal val entries = mutableStateListOf<Entry>()

	internal fun upsert(inId: Any, inContent: @Composable () -> Unit) {
		val vExisting = entries.indexOfFirst { it.id === inId }
		if (vExisting >= 0) {
			entries[vExisting].content = inContent
		} else {
			entries.add(Entry(inId, inContent))
		}
	}

	internal fun remove(inId: Any) {
		entries.removeAll { it.id === inId }
	}
}

/* CompositionLocal that the composeWindow() entry point installs. Reading it
   without a host installed surfaces a clear error rather than silently
   ignoring the popup — popups outside a composeWindow root are not
   supported. */
val LocalPopupHost = compositionLocalOf<PopupHostState> {
	error("No PopupHostState in composition — popups must be hosted by composeWindow(...).")
}

/* Helper used by composeWindow to obtain the host state. Constructing
   PopupHostState requires the internal visibility, so the public factory is
   exposed here. */
fun createPopupHostState(): PopupHostState = PopupHostState()

// ==================
// MARK: PopupLayer
// ==================

/* The host's overlay renderer. Goes at the end of the root composition so
   its children draw above the main tree. */
@Composable
fun PopupLayer(inHost: PopupHostState) {
	// Iterate by id so each popup re-composes with its own state.
	for (vEntry in inHost.entries) {
		key(vEntry.id) { vEntry.content() }
	}
}

// ==================
// MARK: Popup
// ==================

/* Low-level overlay primitive. Renders `content` at the root of the window
   (above the main tree). Positioning is the caller's responsibility — use
   Modifier.offset(...) or wrap in a Box(contentAlignment = ...). For modal
   blocking, set `modal = true` and the popup is wrapped in a fullscreen
   scrim that intercepts pointer events. */
@Composable
fun Popup(
	onDismissRequest: () -> Unit = {},
	modal: Boolean = false,
	scrimColor: Color = if (modal) Color(0x80000000L) else Color.Transparent,
	content: @Composable () -> Unit,
) {
	val vHost = LocalPopupHost.current
	val vId = remember { Any() }
	SideEffect {
		vHost.upsert(vId) {
			if (modal) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(scrimColor)
						.clickable { onDismissRequest() },
					contentAlignment = Alignment.Center,
				) { content() }
			} else {
				content()
			}
		}
	}
	DisposableEffect(Unit) {
		onDispose { vHost.remove(vId) }
	}
}

// ==================
// MARK: PositionedPopup
// ==================

/* Convenience overlay anchored at an absolute window position. Used by
   DropdownMenu and ContextMenu. The full screen is still occupied by a
   click-catcher that closes the popup when the user clicks elsewhere
   (non-modal dismiss). */
@Composable
fun PositionedPopup(
	x: Dp,
	y: Dp,
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
) {
	Popup(onDismissRequest = onDismissRequest, modal = false) {
		Box(modifier = Modifier.fillMaxSize().clickable { onDismissRequest() }) {
			Box(modifier = Modifier.offset(x, y)) { content() }
		}
	}
}
