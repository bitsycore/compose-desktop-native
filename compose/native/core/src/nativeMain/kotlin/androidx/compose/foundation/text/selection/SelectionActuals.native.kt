package androidx.compose.foundation.text.selection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.DpSize

// ==================
// MARK: Selection engine — native actuals
// ==================

/*
 Actuals for the `expect` declarations upstream distributes across
 macosMain / iosMain / webMain / desktopMain / androidMain (never
 nativeMain/skikoMain — those source sets don't carry these). Our flat
 nativeMain covers the whole cross-platform native surface, so we
 hand-write them here.

 TODO: If we grow proper per-platform source sets, split these into
 native/macos/linux/mingw where behaviour genuinely diverges. Today the
 SDL renderer's identical for all three, so one set of actuals covers all.
*/

/* Cmd on macOS, Ctrl elsewhere. We don't know which platform at compile
   time — accept both, matches Ctrl+C on Windows/Linux and Cmd+C on macOS.
   Also honours the dedicated Copy key. */
internal actual fun isCopyKeyEvent(keyEvent: KeyEvent): Boolean =
	keyEvent.key == Key.Copy ||
	(keyEvent.key == Key.C && (keyEvent.isMetaPressed || keyEvent.isCtrlPressed))

/* No selection magnifier on desktop — mobile-only affordance. */
internal actual fun Modifier.selectionMagnifier(manager: SelectionManager): Modifier = this

/* Desktop text-context-menu doesn't add SelectionContainer-specific
   items yet. TODO(CMP-7819): wire the paste-into-selection / clear
   / select-all extras through here when the toolbar lands. */
internal actual fun Modifier.addSelectionContainerTextContextMenuComponents(
	selectionManager: SelectionManager
): Modifier = this

/* Default long-press adjustment on desktop = Word (matches upstream macOS). */
internal actual val FirstLongPressSelectionAdjustment: SelectionAdjustment
	get() = SelectionAdjustment.Word

/* Mobile-style draggable selection handle. Desktop never shows one —
   mouse drag directly extends the selection, no handle bubble.
   TODO: If touch input lands (SDL_EVENT_FINGER_*), route to a real
   handle Composable driven by SelectionHandleInfo. */
@Composable
internal actual fun SelectionHandle(
	offsetProvider: OffsetProvider,
	isStartHandle: Boolean,
	direction: ResolvedTextDirection,
	handlesCrossed: Boolean,
	minTouchTargetSize: DpSize,
	lineHeight: Float,
	modifier: Modifier,
) {
	// no-op — no handle rendered
}
