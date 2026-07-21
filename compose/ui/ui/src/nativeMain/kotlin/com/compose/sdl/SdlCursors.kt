package com.compose.sdl

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerIconCrosshair
import androidx.compose.ui.input.pointer.pointerIconHand
import androidx.compose.ui.input.pointer.pointerIconText
import cnames.structs.SDL_Cursor
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import sdl3.SDL_CreateSystemCursor
import sdl3.SDL_SetCursor
import sdl3.SDL_SystemCursor

// ==================
// MARK: SDL cursor manager
// ==================

/**
 * Maps the port's PointerIcon actuals to SDL system cursors and applies them
 * globally via SDL_SetCursor. System cursors are created lazily and cached
 * (SDL_CreateSystemCursor is not free) and the OS cursor is only re-set when the
 * desired one actually changes. Shared by both renderer legs — SDL is the
 * platform layer. Driven from ComposeOwner.processPointerInput, right after the
 * hover pipeline has set the desired icon for the just-processed pointer event.
 *
 * Cursors are process-global and few; they are left to the OS to reclaim at
 * exit rather than destroyed per window (a window close must not free a cursor
 * another window is showing).
 */
@OptIn(ExperimentalForeignApi::class)
internal object SdlCursors {

	private val cache = HashMap<SDL_SystemCursor, CPointer<SDL_Cursor>?>()
	private var applied: SDL_SystemCursor? = null

	// Set the OS cursor to match [icon]; no-op when already applied.
	fun apply(icon: PointerIcon) {
		val id = icon.toSystemCursor()
		if (id == applied) return
		applied = id
		val cursor = cache.getOrPut(id) { SDL_CreateSystemCursor(id) }
		if (cursor != null) SDL_SetCursor(cursor)
	}

	// The currently applied system-cursor enum name (test hook: demo --cursortest).
	fun appliedName(): String = applied?.name ?: "none"
}

// Test hook for the demo's --cursortest probe (SdlCursors itself stays internal).
fun appliedCursorName(): String = SdlCursors.appliedName()

// The four canonical PointerIcon actuals map by identity; anything else (a
// custom marker icon) falls back to the default arrow.
private fun PointerIcon.toSystemCursor(): SDL_SystemCursor = when (this) {
	pointerIconText -> SDL_SystemCursor.SDL_SYSTEM_CURSOR_TEXT
	pointerIconCrosshair -> SDL_SystemCursor.SDL_SYSTEM_CURSOR_CROSSHAIR
	pointerIconHand -> SDL_SystemCursor.SDL_SYSTEM_CURSOR_POINTER
	else -> SDL_SystemCursor.SDL_SYSTEM_CURSOR_DEFAULT
}
