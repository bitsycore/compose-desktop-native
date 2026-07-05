package com.compose.desktop.native.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup

// ==================
// MARK: PopupHostState
// ==================

/* Project infrastructure behind the official-shaped Popup composable. None of
   this has an official Compose equivalent, so it lives in the
   com.compose.desktop.native layer rather than androidx.compose.ui.window.

   Renderer for overlay content (Dialogs, Tooltips, DropdownMenus, Snackbars).
   The composeWindow() entry point installs a single PopupHostState into the
   composition's LocalPopupHost and renders every active entry at the root
   level, *after* the main tree. Children are drawn in registration order, so
   later popups stack visually on top of earlier ones.

   Popups register via the `Popup` composable in androidx.compose.ui.window.
   Re-registration on each composition keeps the captured-state lambdas fresh;
   DisposableEffect's onDispose runs when the parent removes the popup from the
   tree, removing the entry from the host. */
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

	// ============
	//  Outside-press dismissal (event-level, non-consuming)
	//  A popup registers its content's window rect + onDismiss here. The window's
	//  press dispatch calls notifyOutsidePress BEFORE resolving the click and
	//  does NOT consume it — so a press outside an open menu/tooltip both
	//  dismisses it AND reaches whatever is under it (no dead "first click").
	//  This replaces the old fullscreen click-catcher, which swallowed that click.

	internal class Dismisser(val id: Any) {
		var x = 0; var y = 0; var w = 0; var h = 0
		var onDismiss: () -> Unit = {}
		fun contains(inX: Int, inY: Int) = inX >= x && inX < x + w && inY >= y && inY < y + h
	}

	private val fDismissers = mutableListOf<Dismisser>()

	internal fun setDismisser(inId: Any, inX: Int, inY: Int, inW: Int, inH: Int, inOnDismiss: () -> Unit) {
		val vD = fDismissers.firstOrNull { it.id === inId } ?: Dismisser(inId).also { fDismissers.add(it) }
		vD.x = inX; vD.y = inY; vD.w = inW; vD.h = inH; vD.onDismiss = inOnDismiss
	}

	internal fun removeDismisser(inId: Any) {
		fDismissers.removeAll { it.id === inId }
	}

	/* Dismiss every registered popup whose content rect does NOT contain the
	   press. Called from the window's press dispatch; never consumes the press. */
	fun notifyOutsidePress(inX: Int, inY: Int) {
		if (fDismissers.isEmpty()) return
		// Copy: an onDismiss typically removes the dismisser (state write).
		for (vD in fDismissers.toList()) {
			if (vD.w > 0 && vD.h > 0 && !vD.contains(inX, inY)) vD.onDismiss()
		}
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
// MARK: PositionedPopup
// ==================

/* Registers an event-level "dismiss on press outside [inX,inY,inW,inH]" with the
   popup host so the dismissing press is NOT consumed (it still reaches whatever
   is under it — no dead first click). The caller supplies the content's window
   rect (position is known; size comes from onSizeChanged). */
@Composable
fun PopupOutsideDismiss(inX: Int, inY: Int, inW: Int, inH: Int, onDismissRequest: () -> Unit) {
	val vHost = LocalPopupHost.current
	val vId = remember { Any() }
	SideEffect { vHost.setDismisser(vId, inX, inY, inW, inH, onDismissRequest) }
	DisposableEffect(Unit) { onDispose { vHost.removeDismisser(vId) } }
}

/* Convenience overlay anchored at an absolute window position. Used by
   Tooltip / ContextMenu. Closes on a press outside its bounds via the host's
   event-level dismissal — so that press also reaches the content under it (no
   fullscreen catcher to swallow it). Not part of official Compose.

   `x`/`y` are LAYOUT PIXELS (matches `LayoutCoordinates.positionInRoot` /
   `IntOffset`), not `Dp`. The layout pass runs in physical pixels (see
   `ComposeWindow` § HiDPI: `LocalDensity` = DPR, constraints in `pixelWidth`),
   so callers that get an anchor position from `onGloballyPositioned` are
   already in the same space — no `.dp` round-trip is needed and doing one
   double-scales the offset on Retina. */
@Composable
fun PositionedPopup(
	x: Int,
	y: Int,
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
) {
	Popup(onDismissRequest = onDismissRequest) {
		var vSize by remember { mutableStateOf(IntSize.Zero) }
		Box(
			modifier = Modifier
				.offset { IntOffset(x, y) }
				.onSizeChanged { vSize = it },
		) { content() }
		PopupOutsideDismiss(x, y, vSize.width, vSize.height, onDismissRequest)
	}
}
