package androidx.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

// ==================
// MARK: Dialog — native actuals for vendored ui.window.Dialog expects
// ==================

/*
 Native actuals for the vendored upstream `ui.window.Dialog.kt`
 (`expect class DialogProperties` + `expect fun Dialog`). Renderer has no OS
 dialog window; we route Dialog through the project's Popup host (same
 mechanism DropdownMenu / material Dialog use) and draw a semi-transparent
 scrim behind the content for modality.

 dismissOnBackPress / dismissOnClickOutside: `dismissOnClickOutside` is
 wired — the scrim itself intercepts clicks and calls `onDismissRequest`.
 `dismissOnBackPress` is source-compat only (SDL desktop doesn't have a
 hardware back gesture; the material `Dialog` composable is what wires ESC).
 usePlatformDefaultWidth is accepted but has no effect (there is no platform
 default width on desktop).
*/

// Skiko's DialogProperties carries extra fields (usePlatformInsets /
// useSoftwareKeyboardInset / scrimColor / animateTransition) that vendored
// material3 file `internal/BasicEdgeToEdgeDialog.skiko.kt` passes explicitly.
// Add them as accept-and-ignore params so upstream call sites compile — none
// have effect on this desktop renderer (no OS window insets, no soft keyboard).
//
// NB: NOT marked @ExperimentalComposeUiApi. The expect `DialogProperties` in
// commonMain isn't marked experimental (see core/src/vendor/…/Dialog.kt), and
// annotating the actual with an experimental marker cascades opt-in to every
// widget that has `properties: DialogProperties = DialogProperties()` as a
// param default — notably m3's AlertDialog, whose default value on the expect
// signature transitively requires opt-in of ExperimentalComposeUiApi at every
// call site. Users would see "This API is experimental" pointed at their own
// `AlertDialog(...)` line even though upstream AlertDialog isn't experimental.
actual class DialogProperties actual constructor(
	actual val dismissOnBackPress: Boolean,
	actual val dismissOnClickOutside: Boolean,
	actual val usePlatformDefaultWidth: Boolean,
) {
	@Suppress("unused")
	constructor(
		dismissOnBackPress: Boolean = true,
		dismissOnClickOutside: Boolean = true,
		usePlatformDefaultWidth: Boolean = true,
		usePlatformInsets: Boolean = true,
		useSoftwareKeyboardInset: Boolean = true,
		scrimColor: Color = Color(0f, 0f, 0f, 0.32f),
		animateTransition: Boolean = false,
	) : this(dismissOnBackPress, dismissOnClickOutside, usePlatformDefaultWidth)
}

@Composable
actual fun Dialog(
	onDismissRequest: () -> Unit,
	properties: DialogProperties,
	content: @Composable () -> Unit,
) {
	Popup(
		alignment = Alignment.Center,
		offset = IntOffset(0, 0),
		onDismissRequest = if (properties.dismissOnClickOutside) onDismissRequest else null,
		properties = PopupProperties(
			focusable = true,
			dismissOnBackPress = properties.dismissOnBackPress,
			dismissOnClickOutside = properties.dismissOnClickOutside,
			clippingEnabled = false,
			usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
		),
	) {
		Box(
			modifier = Modifier.fillMaxSize().background(Color(0f, 0f, 0f, 0.32f)),
			contentAlignment = Alignment.Center,
		) {
			content()
		}
	}
}
