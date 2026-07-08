package com.compose.sdl.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup

// ==================
// MARK: PopupHostState
// ==================

/* Project infrastructure behind the official-shaped Popup composable. None of
   this has an official Compose equivalent, so it lives in the
   com.compose.sdl layer rather than androidx.compose.ui.window.

   Renderer for overlay content (Dialogs, Tooltips, DropdownMenus, Snackbars).
   The composeWindow() entry point installs a single PopupHostState into the
   composition's LocalPopupHost and renders every active entry at the root
   level, *after* the main tree. Children are drawn in registration order, so
   later popups stack visually on top of earlier ones.

   Popups register via the `Popup` composable in androidx.compose.ui.window.
   Re-registration on each composition keeps the captured-state lambdas fresh;
   DisposableEffect's onDispose runs when the parent removes the popup from the
   tree, removing the entry from the host — unless the hosted content opted
   into an exit transition via PopupExitHandle, in which case removal is
   deferred until the content finished animating out (see PopupExitHandle).

   Lives in :ui (not :foundation) — the whole `ui.window` pair (Dialog + Popup +
   PopupHost) uses only androidx.compose.ui.layout.Layout for positioning, no
   foundation.background / .layout.Box / .layout.offset. */
class PopupHostState internal constructor() {

	internal class Entry(val id: Any, var content: @Composable () -> Unit) {
		/* Exit deferral — set (from the hosted content, via PopupExitHandle) when
		   the popup wants to play an exit animation before actual removal.
		   `exiting` flips to true when the owning Popup composable disposes; the
		   hosted content observes it, plays its animation, then finish()es. */
		var hasExitTransition = false
		val exiting = mutableStateOf(false)
	}

	internal val entries = mutableStateListOf<Entry>()

	internal fun upsert(inId: Any, inContent: @Composable () -> Unit) {
		val vExisting = entries.indexOfFirst { it.id === inId }
		if (vExisting >= 0) {
			entries[vExisting].content = inContent
		} else {
			entries.add(Entry(inId, inContent))
		}
	}

	/* Called when the owning Popup composable leaves the composition. Entries
	   whose hosted content registered an exit transition are NOT removed —
	   they switch to `exiting` and stay composed (the host composition owns
	   them) until the content calls PopupExitHandle.finish(). */
	internal fun remove(inId: Any) {
		val vEntry = entries.firstOrNull { it.id === inId } ?: return
		if (vEntry.hasExitTransition) {
			if (!vEntry.exiting.value) vEntry.exiting.value = true
		} else {
			entries.removeAll { it.id === inId }
		}
	}

	/* Unconditional removal — the end of an exit transition. */
	internal fun forceRemove(inId: Any) {
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

val LocalPopupHost = compositionLocalOf<PopupHostState> {
	error("No PopupHostState in composition — popups must be hosted by composeWindow(...).")
}

fun createPopupHostState(): PopupHostState = PopupHostState()

// ==================
// MARK: PopupExitHandle — deferred close for exit animations
// ==================

/* Handle a hosted popup's CONTENT uses to defer its removal for an exit
   animation (mirrors upstream skiko's ComposeSceneLayer surviving the owner's
   disposal so Dialog can animate out).

   Contract: call [enableExitTransition] while composing; when the owning
   Popup composable disposes, the host flips [isExiting] instead of removing
   the entry. The hosted content (still composed — it lives in the HOST's
   composition, not the owner's) observes that, plays its animation, and MUST
   end with [finish], which actually removes the entry. */
class PopupExitHandle internal constructor(
	private val fHost: PopupHostState,       // owning host — target of finish()
	private val fEntry: PopupHostState.Entry, // the entry this handle controls
) {
	/* True once the owning Popup left the composition and the host is waiting
	   for this entry's exit animation. Observable — recomposes the content. */
	val isExiting: State<Boolean> get() = fEntry.exiting

	/* Opt in to exit deferral. Idempotent; call from a SideEffect. */
	fun enableExitTransition() { fEntry.hasExitTransition = true }

	/* Ends the deferral — the entry is removed from the host for real. */
	fun finish() { fHost.forceRemove(fEntry.id) }
}

/* Per-entry handle, provided by PopupLayer around each hosted content. Null
   outside a popup layer. NB: safe to expose through the caller-locals rewrap
   in Popup (CompositionLocalProvider(callerContext)) — the caller never
   provides this local, so the layer's per-entry value stays visible. */
val LocalPopupExitHandle = staticCompositionLocalOf<PopupExitHandle?> { null }

// ==================
// MARK: PopupLayer
// ==================

/* Overlay renderer: goes at the end of the root composition so its children
   draw above the main tree. Keyed by entry id so each popup re-composes with
   its own state. */
@Composable
fun PopupLayer(inHost: PopupHostState) {
	for (vEntry in inHost.entries) {
		key(vEntry.id) {
			val vHandle = remember { PopupExitHandle(inHost, vEntry) }
			CompositionLocalProvider(LocalPopupExitHandle provides vHandle) {
				vEntry.content()
			}
		}
	}
}

// ==================
// MARK: PopupOutsideDismiss
// ==================

/* Registers an event-level "dismiss on press outside [inX,inY,inW,inH]" with the
   popup host so the dismissing press is NOT consumed (it still reaches whatever
   is under it — no dead first click). */
@Composable
fun PopupOutsideDismiss(inX: Int, inY: Int, inW: Int, inH: Int, onDismissRequest: () -> Unit) {
	val vHost = LocalPopupHost.current
	val vId = remember { Any() }
	SideEffect { vHost.setDismisser(vId, inX, inY, inW, inH, onDismissRequest) }
	DisposableEffect(Unit) { onDispose { vHost.removeDismisser(vId) } }
}

// ==================
// MARK: PositionedPopup
// ==================

/* Overlay anchored at an absolute window position. Used by Tooltip / ContextMenu.
   Closes on a press outside its bounds via the host's event-level dismissal.
   `x`/`y` are LAYOUT PIXELS (matches `LayoutCoordinates.positionInRoot`), not
   `Dp` — layout runs in physical pixels under the Option-B density flow. */
@Composable
fun PositionedPopup(
	x: Int,
	y: Int,
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
) {
	Popup(onDismissRequest = onDismissRequest) {
		var vSize by remember { mutableStateOf(IntSize.Zero) }
		// Absolute-position child via a plain Layout — no `Modifier.offset` (that
		// lives in :foundation). The parent Popup layer fills the window, so we
		// report the window's constraints and place the child at (x, y).
		Layout(
			content = content,
			modifier = Modifier.onSizeChanged { vSize = it },
		) { measurables, constraints ->
			val vChildConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
			val vChildren = measurables.map { it.measure(vChildConstraints) }
			// Own size = union of children (usually one). Place all at (x, y).
			val vW = vChildren.maxOfOrNull { it.width } ?: 0
			val vH = vChildren.maxOfOrNull { it.height } ?: 0
			layout(vW, vH) {
				vChildren.forEach { it.place(x, y) }
			}
		}
		PopupOutsideDismiss(x, y, vSize.width, vSize.height, onDismissRequest)
	}
}
