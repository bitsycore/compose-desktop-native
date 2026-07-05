package androidx.compose.foundation.text

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

// ==================
// MARK: TextFieldScroll — native actuals
// ==================

/*
 Byte-identical mirror of upstream desktopMain — no overscroll on desktop,
 textFieldScroll delegates to `defaultTextFieldScroll` (a private helper in
 the vendored `TextFieldScroll.kt`).
*/
@Composable
internal actual fun rememberTextFieldOverscrollEffect(): OverscrollEffect? = null

internal actual fun Modifier.textFieldScroll(
	scrollerPosition: TextFieldScrollerPosition,
	textFieldValue: TextFieldValue,
	visualTransformation: VisualTransformation,
	overscrollEffect: OverscrollEffect?,
	textLayoutResultProvider: () -> TextLayoutResultProxy?,
): Modifier = defaultTextFieldScroll(
	scrollerPosition,
	textFieldValue,
	visualTransformation,
	overscrollEffect,
	textLayoutResultProvider,
)
