package androidx.compose.ui.window

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

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
// MARK: PopupProperties
// ==================

/* Behaviour flags for a Popup. This renderer hosts overlay content without a
   focus system, so focusable / dismissOnBackPress / dismissOnClickOutside /
   clippingEnabled are accepted for source-compatibility with official Compose
   but not all are acted on — callers handle outside-click dismissal via their
   own click-catcher (see DropdownMenu) and Dialog draws its own scrim. */
class PopupProperties(
	val focusable: Boolean = false,
	val dismissOnBackPress: Boolean = true,
	val dismissOnClickOutside: Boolean = true,
	val clippingEnabled: Boolean = true,
	val usePlatformDefaultWidth: Boolean = true,
) {
	override fun equals(other: Any?): Boolean =
		other is PopupProperties &&
			focusable == other.focusable &&
			dismissOnBackPress == other.dismissOnBackPress &&
			dismissOnClickOutside == other.dismissOnClickOutside &&
			clippingEnabled == other.clippingEnabled &&
			usePlatformDefaultWidth == other.usePlatformDefaultWidth

	override fun hashCode(): Int {
		var result = focusable.hashCode()
		result = 31 * result + dismissOnBackPress.hashCode()
		result = 31 * result + dismissOnClickOutside.hashCode()
		result = 31 * result + clippingEnabled.hashCode()
		result = 31 * result + usePlatformDefaultWidth.hashCode()
		return result
	}
}

// ==================
// MARK: Popup
// ==================

/* Overlay primitive matching official Compose's signature. Renders `content`
   at the root of the window (above the main tree), positioned by `alignment`
   within the window and shifted by `offset` (logical points). Outside-click
   dismissal and modality are the caller's responsibility (Dialog draws a
   scrim; DropdownMenu installs its own click-catcher) — see PopupProperties. */
@Composable
fun Popup(
	alignment: Alignment = Alignment.TopStart,
	offset: IntOffset = IntOffset(0, 0),
	onDismissRequest: (() -> Unit)? = null,
	properties: PopupProperties = PopupProperties(),
	content: @Composable () -> Unit,
) {
	val vHost = LocalPopupHost.current
	val vId = remember { Any() }
	// Snapshot the CompositionLocals in scope at the call site. PopupLayer renders
	// the hosted content at the composition root, so without re-providing these it
	// would only see the root defaults — MaterialTheme and app-level locals set
	// further down the tree would never reach the popup.
	val vLocals = currentCompositionLocalContext
	// Default (TopStart / no offset) renders content verbatim — callers that
	// position themselves (DropdownMenu, Snackbar) are unaffected. Otherwise wrap
	// in a fullscreen aligner + offset.
	val vPositioned: @Composable () -> Unit =
		if (alignment == Alignment.TopStart && offset.x == 0 && offset.y == 0) {
			content
		} else {
			{
				Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
					Box(modifier = Modifier.offset(offset.x.dp, offset.y.dp)) { content() }
				}
			}
		}
	SideEffect {
		vHost.upsert(vId) {
			CompositionLocalProvider(vLocals) { vPositioned() }
		}
	}
	DisposableEffect(Unit) {
		onDispose { vHost.remove(vId) }
	}
}

// ==================
// MARK: PositionedPopup (non-official project helper)
// ==================

/* Convenience overlay anchored at an absolute window position. Used by
   Tooltip / ContextMenu. A fullscreen click-catcher closes the popup when the
   user clicks elsewhere. Not part of official Compose — prefer Popup(offset).*/
@Composable
fun PositionedPopup(
	x: Dp,
	y: Dp,
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
) {
	Popup(onDismissRequest = onDismissRequest) {
		Box(modifier = Modifier.fillMaxSize().clickable { onDismissRequest() }) {
			Box(modifier = Modifier.offset(x, y)) { content() }
		}
	}
}
