package androidx.compose.foundation.text.input.internal

import androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange

// ==================
// MARK: TextFieldCoreModifier — native actuals
// ==================

/*
 Byte-identical mirror of upstream macosMain — both delegate to the default
 selection-highlight and cursor draw helpers baked into the vendored
 TextFieldCoreModifier.kt.
*/
internal actual fun TextFieldCoreModifierNode.drawSelectionHighlight(
	scope: DrawScope,
	selection: TextRange,
	textLayoutResult: TextLayoutResult,
) = drawDefaultSelectionHighlight(scope, selection, textLayoutResult)

internal actual fun TextFieldCoreModifierNode.drawCursor(
	scope: DrawScope,
	brush: Brush,
	showCursor: Boolean,
	cursorAnimation: CursorAnimationState?,
	textFieldSelectionState: TextFieldSelectionState,
) = drawDefaultCursor(scope, brush, showCursor, cursorAnimation, textFieldSelectionState)
