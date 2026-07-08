package androidx.compose.foundation.text

import androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState
import androidx.compose.foundation.text.selection.SelectionManager
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.runtime.Composable

// ==================
// MARK: ContextMenu — native actuals
// ==================

/*
 Mirrors upstream macosMain / desktopMain / iosMain — all three overloads
 delegate to CommonContextMenuArea (vendored). This unblocks vendored
 BasicTextField / SelectionContainer / CoreTextField call sites.
*/

@Composable
internal actual fun ContextMenuArea(
	manager: TextFieldSelectionManager,
	content: @Composable () -> Unit,
) = CommonContextMenuArea(manager, content)

@Composable
internal actual fun ContextMenuArea(
	selectionState: TextFieldSelectionState,
	enabled: Boolean,
	content: @Composable () -> Unit,
) = CommonContextMenuArea(selectionState, enabled, content)

@Composable
internal actual fun ContextMenuArea(
	manager: SelectionManager,
	content: @Composable () -> Unit,
) = CommonContextMenuArea(manager, content)
