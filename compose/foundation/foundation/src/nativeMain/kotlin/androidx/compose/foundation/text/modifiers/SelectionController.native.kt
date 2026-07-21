package androidx.compose.foundation.text.modifiers

import androidx.compose.foundation.text.selection.SelectionRegistrar
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates

// ==================
// MARK: makeSelectionModifier — native actual
// ==================

/**
 Mirrors upstream desktopMain / macosMain / iosMain actuals — all just
 delegate to `makeDefaultSelectionModifier`. That helper lives inside
 `SelectionController.kt` (vendored) and installs a SelectionModifierNode
 wiring gestures + hover pointer icon + BringIntoViewRequester through
 the registrar.
*/
internal actual fun SelectionRegistrar.makeSelectionModifier(
	selectableId: Long,
	layoutCoordinatesProvider: () -> LayoutCoordinates?,
): Modifier = makeDefaultSelectionModifier(selectableId, layoutCoordinatesProvider)
