package androidx.compose.foundation.text

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: LegacyTextFieldState — extract (byte-identical from upstream CoreTextField.kt)
// ==================

/*
 Byte-identical extract of the `LegacyTextFieldState` class + supporting
 helpers from upstream `foundation.text.CoreTextField.kt` (1200+L). We
 can't vendor the whole CoreTextField.kt yet — it needs
 `createLegacyPlatformTextInputServiceAdapter` (blocked on TextInputSession.skiko
 which needs a full IME wire-up), `interceptDPadAndMoveFocus`,
 `SkikoPlatformTextInputMethodRequest`, and other Skia-plumbing helpers.
 But TextFieldSelectionManager + CoreTextFieldSemanticsModifier both
 reference LegacyTextFieldState heavily — extract it here so those can
 vendor.

 TODO: delete this file once CoreTextField.kt can vendor cleanly.
*/

internal interface HeightForSingleLineFieldProvider {
	var heightForSingleLineField: Dp
}

internal class LegacyTextFieldState(
	var textDelegate: TextDelegate,
	val recomposeScope: RecomposeScope,
	val keyboardController: SoftwareKeyboardController?,
) : HeightForSingleLineFieldProvider {
	val processor = EditProcessor()
	var inputSession: TextInputSession? = null

	var hasFocus by mutableStateOf(false)

	override var heightForSingleLineField by mutableStateOf(0.dp)

	private var _layoutCoordinates: LayoutCoordinates? = null
	var layoutCoordinates: LayoutCoordinates?
		get() = _layoutCoordinates?.takeIf { it.isAttached }
		set(value) { _layoutCoordinates = value }

	private val layoutResultState: MutableState<TextLayoutResultProxy?> = mutableStateOf(null)
	var layoutResult: TextLayoutResultProxy?
		get() = layoutResultState.value
		set(value) {
			layoutResultState.value = value
			isLayoutResultStale = false
		}

	var untransformedText: AnnotatedString? = null

	var handleState by mutableStateOf(HandleState.None)
	var showFloatingToolbar by mutableStateOf(false)
	var showSelectionHandleStart by mutableStateOf(false)
	var showSelectionHandleEnd by mutableStateOf(false)
	var showCursorHandle by mutableStateOf(false)

	var isLayoutResultStale: Boolean = true
		private set

	var isInTouchMode: Boolean by mutableStateOf(true)

	private val keyboardActionRunner: KeyboardActionRunner =
		KeyboardActionRunner(keyboardController)

	var autofillHighlightOn by mutableStateOf(false)
	var justAutofilled by mutableStateOf(false)

	private var onValueChangeOriginal: (TextFieldValue) -> Unit = {}

	val onValueChange: (TextFieldValue) -> Unit = {
		if (it.text != untransformedText?.text) {
			handleState = HandleState.None
			if (justAutofilled) {
				justAutofilled = false
			} else {
				autofillHighlightOn = false
			}
		}
		selectionPreviewHighlightRange = TextRange.Zero
		deletionPreviewHighlightRange = TextRange.Zero
		onValueChangeOriginal(it)
		recomposeScope.invalidate()
	}

	val onImeActionPerformed: (ImeAction) -> Unit = { imeAction ->
		keyboardActionRunner.runAction(imeAction)
	}
	val onImeActionPerformedWithResult: (ImeAction) -> Boolean = { imeAction ->
		keyboardActionRunner.runAction(imeAction)
	}

	val highlightPaint: Paint = Paint()
	var selectionBackgroundColor = Color.Unspecified

	var selectionPreviewHighlightRange: TextRange by mutableStateOf(TextRange.Zero)
	var deletionPreviewHighlightRange: TextRange by mutableStateOf(TextRange.Zero)

	fun hasHighlight() =
		!selectionPreviewHighlightRange.collapsed || !deletionPreviewHighlightRange.collapsed

	fun update(
		untransformedText: AnnotatedString,
		visualText: AnnotatedString,
		textStyle: TextStyle,
		softWrap: Boolean,
		density: Density,
		fontFamilyResolver: FontFamily.Resolver,
		onValueChange: (TextFieldValue) -> Unit,
		keyboardActions: KeyboardActions,
		focusManager: FocusManager,
		selectionBackgroundColor: Color,
	) {
		this.onValueChangeOriginal = onValueChange
		this.selectionBackgroundColor = selectionBackgroundColor
		this.keyboardActionRunner.apply {
			this.keyboardActions = keyboardActions
			this.focusManager = focusManager
		}
		this.untransformedText = untransformedText

		val newTextDelegate =
			updateTextDelegate(
				current = textDelegate,
				text = visualText,
				style = textStyle,
				softWrap = softWrap,
				density = density,
				fontFamilyResolver = fontFamilyResolver,
				placeholders = emptyList(),
			)

		if (textDelegate !== newTextDelegate) isLayoutResultStale = true
		textDelegate = newTextDelegate
	}
}

/** Request focus on tap. If already focused, makes sure the keyboard is requested. */
internal fun requestFocusAndShowKeyboardIfNeeded(
	state: LegacyTextFieldState,
	focusRequester: FocusRequester,
	allowKeyboard: Boolean,
) {
	if (!state.hasFocus) {
		focusRequester.requestFocus()
	} else if (allowKeyboard) {
		state.keyboardController?.show()
	}
}
