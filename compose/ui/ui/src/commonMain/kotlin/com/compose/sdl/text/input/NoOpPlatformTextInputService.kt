package com.compose.sdl.text.input

import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue

// ==================
// MARK: NoOpPlatformTextInputService
// ==================

/*
 A no-op `PlatformTextInputService` used by ComposeOwner / StubOwner /
 RootForTest to satisfy the deprecated `TextInputService` field the vendored
 Owner surface still exposes. Modern text input goes through
 `PlatformTextInputModifierNode` instead of this pipeline.

 If the SDL IME wire-up is added later (via SDL_StartTextInput /
 SDL_TEXT_INPUT_EVENT), replace this with a real implementation.
*/
@Suppress("DEPRECATION")
internal object NoOpPlatformTextInputService : PlatformTextInputService {
	override fun startInput(
		value: TextFieldValue,
		imeOptions: ImeOptions,
		onEditCommand: (List<EditCommand>) -> Unit,
		onImeActionPerformed: (ImeAction) -> Unit,
	) {}
	override fun startInput() {}
	override fun stopInput() {}
	override fun showSoftwareKeyboard() {}
	override fun hideSoftwareKeyboard() {}
	override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {}
}
