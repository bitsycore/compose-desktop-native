package androidx.compose.foundation.text.input.internal.selection

import androidx.compose.foundation.text.input.internal.TextLayoutState
import androidx.compose.foundation.text.input.internal.TransformedTextFieldState

// ==================
// MARK: TextFieldMagnifierNode — native actual (no-op)
// ==================

/*
 Mirrors upstream macosMain / desktopMain — no magnifier on desktop.
*/
internal actual fun textFieldMagnifierNode(
	textFieldState: TransformedTextFieldState,
	textFieldSelectionState: TextFieldSelectionState,
	textLayoutState: TextLayoutState,
	visible: Boolean,
): TextFieldMagnifierNode {
	return object : TextFieldMagnifierNode() {
		override fun update(
			textFieldState: TransformedTextFieldState,
			textFieldSelectionState: TextFieldSelectionState,
			textLayoutState: TextLayoutState,
			visible: Boolean,
		) {}
	}
}
