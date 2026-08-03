package androidx.compose.foundation.text.selection

import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope

// ==================
// MARK: TextFieldSelectionManager — native actuals
// ==================

/**
 Byte-identical mirror of upstream macosMain — magnification and toolbar
 context-menu components are desktop no-ops. `isSelectionHandleInVisibleBound`
 delegates to the default-inside-visible-rect check (defined in the vendored
 TextFieldSelectionManager.kt).
*/

internal actual fun Modifier.textFieldMagnifier(manager: TextFieldSelectionManager): Modifier = this

internal actual fun TextFieldSelectionManager.isSelectionHandleInVisibleBound(
	isStartHandle: Boolean,
): Boolean = isSelectionHandleInVisibleBoundDefault(isStartHandle)

// NO-OP by design: this seam belongs to the NEW text-context-menu API
// (ComposeFoundationFlags.isNewContextMenuEnabled = false on native), so it's
// unreachable. The working legacy-field right-click menu goes through the vendored
// CommonContextMenuArea path (TextFieldSelectionManager.contextMenuBuilder → Cut /
// Copy / Paste / SelectAll).
internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
	manager: TextFieldSelectionManager,
	coroutineScope: CoroutineScope,
): Modifier = this

