package com.compose.sdl.text.input

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.SetComposingTextCommand

// ==================
// MARK: ImeBridge — SDL text events <-> the active text-input session
// ==================

/**
 * Connects SDL's text events to the focused text field's IME session. The owner's
 * textInputSession registers the active PlatformTextInputMethodRequest here when a
 * field gains focus (and clears it on focus loss); ComposeWindow feeds
 * SDL_EVENT_TEXT_INPUT (committed) through commit() and SDL_EVENT_TEXT_EDITING
 * (preedit/composition) through compose(). When no session is active the window
 * falls back to the synthetic-KeyEvent path (dispatchTypedText).
 *
 * Committed text goes through CommitTextCommand, not a synthetic key, because a
 * commit must REPLACE the current composing region with the final text — the
 * synthetic-key path has no notion of a composition and would leave the preedit in
 * place. For plain (non-composing) Latin typing CommitTextCommand simply inserts,
 * so the committed-text path is unchanged.
 */
@OptIn(ExperimentalComposeUiApi::class)
object ImeBridge {

	// The focused field's request, or null when no text field is focused.
	var request: PlatformTextInputMethodRequest? = null
		private set

	// Invoked when a text-input session becomes active (a field gains focus) or
	// inactive (focus lost). The window wires this to place the OS IME candidate
	// window at the focused field's rect on focus-gain — otherwise the candidate
	// popup is only positioned on the first TEXT_EDITING event, mis-placing it for
	// the first keystroke. Repointed per window in installGlobals().
	var onSessionActiveChange: ((active: Boolean) -> Unit)? = null

	// Set / cleared by ComposeOwner.textInputSession as fields gain / lose focus.
	fun setRequest(inRequest: PlatformTextInputMethodRequest?) {
		val wasActive = request != null
		request = inRequest
		val nowActive = inRequest != null
		if (wasActive != nowActive) onSessionActiveChange?.invoke(nowActive)
	}

	// SDL_EVENT_TEXT_INPUT: commit text, replacing any active composition. Returns
	// true if a session consumed it (window then skips the synthetic-key fallback).
	fun commit(inText: String): Boolean {
		val vRequest = request ?: return false
		vRequest.onEditCommand(listOf(CommitTextCommand(AnnotatedString(inText), 1)))
		return true
	}

	// SDL_EVENT_TEXT_EDITING: set the composing (preedit) region; empty text clears it.
	fun compose(inText: String): Boolean {
		val vRequest = request ?: return false
		vRequest.onEditCommand(listOf(SetComposingTextCommand(AnnotatedString(inText), 1)))
		return true
	}
}
