package com.compose.sdl

// ==================
// MARK: WindowAttributes
// ==================

/**
 The Compose Desktop `Window()` attributes this port supports, bundled so they
 travel as one value from the composable down to [WindowInstance] instead of as
 six positional booleans.

 [transparent] is creation-only: SDL needs `SDL_WINDOW_TRANSPARENT` at
 `SDL_CreateWindow` time and offers no setter, so changing it after the window
 exists has no effect. The rest are re-applied whenever `Window()` recomposes
 with a new value.
 */
internal data class WindowAttributes(
	/** Hides the title bar and border (`SDL_WINDOW_BORDERLESS`). */
	val undecorated: Boolean = false,
	/** Per-pixel alpha for the window surface. CREATION-ONLY - see above. */
	val transparent: Boolean = false,
	/** Whether the user can resize the window. */
	val resizable: Boolean = true,
	/** False drops input (pointer / key / text / wheel / drop) while the window
	   keeps rendering - Compose Desktop's `enabled = false` behaviour. */
	val enabled: Boolean = true,
	/** False stops the window taking keyboard focus when clicked or raised. */
	val focusable: Boolean = true,
	/** Keeps the window above other windows. */
	val alwaysOnTop: Boolean = false,
)
