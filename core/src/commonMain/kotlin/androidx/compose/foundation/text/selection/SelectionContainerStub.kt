package androidx.compose.foundation.text.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ==================
// MARK: SelectionContainer — project stub
// ==================

/*
 Upstream `SelectionContainer` needs `SelectionManager` + the whole selection-
 gesture / handles / context-menu engine (`SelectionManager.kt` 1804L,
 `TextFieldSelectionManager.kt` 1503L, `SelectionGestures.kt` 368L, etc.) —
 none of which are vendored yet. The engine also expects each descendant text
 leaf to expose a `TextLayoutResult` via a rich `Selectable` (getBoundingBox /
 getHandlePosition / getLineHeight / getSelectAllSelection) — our text leaf
 currently doesn't produce a `TextLayoutResult`.

 So while the vendored leaves (`Selectable`, `SelectionRegistrar`,
 `SelectionRegistrarImpl`, `Selection`, `SelectionAdjustment`,
 `SelectionLayout`, `SelectionHelpers`, `TextSelectionColors`,
 `TextSelectionDelegate`) provide the interface surface, the actual selection
 flow doesn't work yet — this pass-through keeps existing call sites (apidemo
 `ViewerPanel.kt`) compiling. Real behaviour lands when the manager +
 `TextLayoutResult` production are vendored.

 Delete this file once upstream `SelectionContainer.kt` can vendor cleanly.
*/
@Composable
fun SelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	Box(modifier = modifier) { content() }
}

@Composable
fun DisableSelection(content: @Composable () -> Unit) {
	content()
}
