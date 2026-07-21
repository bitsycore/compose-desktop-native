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

	// Set / cleared by ComposeOwner.textInputSession as fields gain / lose focus.
	fun setRequest(inRequest: PlatformTextInputMethodRequest?) {
		request = inRequest
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
