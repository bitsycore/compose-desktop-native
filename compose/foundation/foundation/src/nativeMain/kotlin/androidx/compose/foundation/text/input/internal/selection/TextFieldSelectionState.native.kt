package androidx.compose.foundation.text.input.internal.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.TextDragObserver
import androidx.compose.foundation.text.selection.MouseSelectionObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.CoroutineScope

// ==================
// MARK: TextFieldSelectionState — native actuals
// ==================

/*
 Byte-close mirror of upstream macosMain — desktop tap / selection gestures
 delegate to the default helpers baked into TextFieldSelectionState.
 addBasicTextFieldTextContextMenuComponents = no-op (TODO CMP-7819).
 ClipboardPasteState uses our project Clipboard's plaintext presence — no
 NSPasteboard access on native.
*/

internal actual suspend fun TextFieldSelectionState.detectTextFieldTapGestures(
	pointerInputScope: PointerInputScope,
	interactionSource: MutableInteractionSource?,
	requestFocus: () -> Unit,
	showKeyboard: () -> Unit,
) = defaultDetectTextFieldTapGestures(pointerInputScope, interactionSource, requestFocus, showKeyboard)

internal actual suspend fun TextFieldSelectionState.textFieldSelectionGestures(
	pointerInputScope: PointerInputScope,
	mouseSelectionObserver: MouseSelectionObserver,
	textDragObserver: TextDragObserver,
) = pointerInputScope.defaultTextFieldSelectionGestures(mouseSelectionObserver, textDragObserver)

internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
	state: TextFieldSelectionState,
	coroutineScope: CoroutineScope,
): Modifier = this

// TODO: real hasClip requires access to NSPasteboard on macOS / SDL clipboard
// pasteboard items on desktop. Our project Clipboard only tracks plain text,
// so hasClip = hasText for now.
internal actual class ClipboardPasteState actual constructor(private val clipboard: Clipboard) {
	private var _hasText = false

	actual val hasText: Boolean get() = _hasText
	actual val hasClip: Boolean get() = _hasText

	actual suspend fun update() {
		_hasText = try {
			(clipboard.getClipEntry()?.getPlainText()?.isNotEmpty()) ?: false
		} catch (_: Throwable) {
			false
		}
	}
}
