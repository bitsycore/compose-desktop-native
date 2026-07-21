package androidx.compose.foundation.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue

// ==================
// MARK: CoreTextField / TextFieldKeyInput / TextFieldPointerModifier — native actuals
// ==================

/**
 Byte-identical mirror of upstream macosMain — all three CoreTextField
 expects delegate to default helpers baked into CoreTextField.kt /
 TextFieldPointerModifier.common.kt. `isTypedEvent` uses the desktop-shape
 filter (not ISO control, not AppKit reserved, not Meta/Ctrl).
*/

internal actual fun Modifier.textFieldCursor(
	state: LegacyTextFieldState,
	value: TextFieldValue,
	offsetMapping: OffsetMapping,
	cursorBrush: Brush,
	showCursor: Boolean,
): Modifier = cursor(state, value, offsetMapping, cursorBrush, showCursor)

internal actual fun Modifier.textFieldDraw(
	state: LegacyTextFieldState,
	value: TextFieldValue,
	offsetMapping: OffsetMapping,
): Modifier = defaultTextFieldDraw(state, value, offsetMapping)

@Composable
internal actual fun Modifier.textFieldPointer(
	manager: TextFieldSelectionManager,
	enabled: Boolean,
	interactionSource: MutableInteractionSource?,
	state: LegacyTextFieldState,
	focusRequester: FocusRequester,
	readOnly: Boolean,
	offsetMapping: OffsetMapping,
): Modifier = defaultTextFieldPointer(
	manager,
	enabled,
	interactionSource,
	state,
	focusRequester,
	readOnly,
	offsetMapping,
)

internal actual val KeyEvent.isTypedEvent: Boolean
	get() = type == KeyEventType.KeyDown &&
		!isISOControl(utf16CodePoint) &&
		!isAppKitReserved(utf16CodePoint) &&
		!isMetaPressed &&
		!isCtrlPressed

private fun isISOControl(codePoint: Int): Boolean =
	codePoint in 0x00..0x1F || codePoint in 0x7F..0x9F

private fun isAppKitReserved(codePoint: Int): Boolean =
	codePoint in 0xF700..0xF8FF
