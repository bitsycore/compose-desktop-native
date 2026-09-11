@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.compose.sdl

import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.compose.sdl.node.ComposeRootHost

// ==================
// MARK: Input helpers
// ==================

/** Escape → back: port of upstream desktop's BackNavigationEventInput. An
   unconsumed Escape KeyDown completes a back navigation on the dispatcher
   this input is registered with (BackHandler / PredictiveBackHandler
   consumers: m3 SearchBar collapse, dialog dismissal, …). */
internal class BackNavigationInput : androidx.navigationevent.NavigationEventInput() {
	fun onKeyEvent(inEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
		if (inEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
			inEvent.key == androidx.compose.ui.input.key.Key.Escape
		) {
			dispatchOnBackCompleted()
			return true
		}
		return false
	}
}

/** Re-dispatches committed text (SDL TEXT_INPUT) as one synthetic typed
   KeyDown per Unicode codepoint (surrogate pairs folded). Key.Unknown +
   codePoint + no modifiers matches both vendored text stacks' isTypedEvent
   criteria; the SDL key mapper leaves codePoint = 0 on real key events, so
   the physical KeyDown and the synthetic one can never double-insert. */
internal fun dispatchTypedText(inHost: ComposeRootHost, inText: String) {
	var vI = 0
	while (vI < inText.length) {
		val vHigh = inText[vI]
		val vCodepoint: Int
		if (vHigh.isHighSurrogate() && vI + 1 < inText.length && inText[vI + 1].isLowSurrogate()) {
			vCodepoint = 0x10000 + ((vHigh.code - 0xD800) shl 10) + (inText[vI + 1].code - 0xDC00)
			vI += 2
		} else {
			vCodepoint = vHigh.code
			vI += 1
		}
		inHost.dispatchKeyEvent(
			androidx.compose.ui.input.key.KeyEvent(
				key = androidx.compose.ui.input.key.Key.Unknown,
				type = androidx.compose.ui.input.key.KeyEventType.KeyDown,
				codePoint = vCodepoint,
			),
		)
	}
}
