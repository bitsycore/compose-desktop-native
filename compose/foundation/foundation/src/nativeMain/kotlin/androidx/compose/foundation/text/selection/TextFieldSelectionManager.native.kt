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

// TODO(CMP-7819): wire text-field context-menu components when the desktop toolbar lands.
internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
	manager: TextFieldSelectionManager,
	coroutineScope: CoroutineScope,
): Modifier = this

