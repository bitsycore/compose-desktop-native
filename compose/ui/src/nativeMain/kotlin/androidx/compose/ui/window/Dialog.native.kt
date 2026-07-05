package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

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

 Lives in :ui (not :foundation) — the whole ui.window pair is :ui-only, using
 Layout + drawBehind for the scrim instead of Box + Modifier.background.
*/

// Skiko's DialogProperties carries extra fields (usePlatformInsets /
// useSoftwareKeyboardInset / scrimColor / animateTransition) that vendored
// material3 file `internal/BasicEdgeToEdgeDialog.skiko.kt` passes explicitly.
// Add them as accept-and-ignore params so upstream call sites compile — none
// have effect on this desktop renderer (no OS window insets, no soft keyboard).
//
// NB: NOT marked @ExperimentalComposeUiApi. The expect `DialogProperties` in
// commonMain isn't marked experimental (see ui/src/vendor/…/Dialog.kt), and
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
		// Scrim + centering, expressed as a single Layout that fills the window
		// constraints, draws a 32% black scrim in the background, and centers
		// the content. Replaces the old Box(fillMaxSize().background()) — Box
		// and Modifier.background both live in :foundation, but ui.window has
		// no business pulling those in, so we do the same via ui-only
		// primitives (Layout + drawBehind).
		Layout(
			content = content,
			modifier = Modifier.drawBehind {
				val vSize = size
				drawContext.canvas.drawRect(
					left = 0f, top = 0f, right = vSize.width, bottom = vSize.height,
					paint = Paint().apply { color = Color(0f, 0f, 0f, 0.32f) },
				)
			},
		) { measurables, constraints ->
			val vChildConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
			val vChildren = measurables.map { it.measure(vChildConstraints) }
			val vChildW = vChildren.maxOfOrNull { it.width } ?: 0
			val vChildH = vChildren.maxOfOrNull { it.height } ?: 0
			val vLayoutW = constraints.maxWidth
			val vLayoutH = constraints.maxHeight
			layout(vLayoutW, vLayoutH) {
				val vX = (vLayoutW - vChildW) / 2
				val vY = (vLayoutH - vChildH) / 2
				vChildren.forEach { it.place(vX, vY) }
			}
		}
	}
}

// Suppress unused-import warnings — these types (Rect, Size, IntSize) are
// referenced through drawContext.canvas.drawRect / size which use them
// implicitly at call sites the compiler picks up via receivers.
@Suppress("unused") private val vSuppressUnused: Any = listOf(Rect.Zero, Size.Zero, IntSize.Zero)
