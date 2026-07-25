package com.compose.sdl.text

// ==================
// MARK: Viewport size (project-only)
// ==================

/** Logical size of the window, set by the render loop each frame. Lets commonMain
   composables (selection highlights cull per-line work, DropdownMenu flips/clamps
   itself) read the viewport without a hard dependency on the window layer.
   0 until first set — callers treat that as "viewport unknown". */
var currentViewportHeight: Int = 0
var currentViewportWidth: Int = 0
