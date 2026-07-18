package androidx.compose.ui.input.pointer

// ==================
// MARK: PointerIcon actuals — SDL3 cursors
// ==================

/*
 Actuals for vendored PointerIcon.kt. Each of the four canonical icons is a
 distinct marker object; com.compose.sdl.SdlCursors maps them BY IDENTITY to
 SDL_SYSTEM_CURSOR_*, and ComposeOwner's pointerIconService applies the cursor
 via SDL_SetCursor after each hover-processed pointer event. Lives in nativeMain
 so both renderer legs share it (SDL is the platform layer); a custom marker
 icon that isn't one of these four maps to the default arrow.
*/

private class SdlCursor(@Suppress("unused") val name: String) : PointerIcon

internal actual val pointerIconDefault: PointerIcon = SdlCursor("default")
internal actual val pointerIconCrosshair: PointerIcon = SdlCursor("crosshair")
internal actual val pointerIconText: PointerIcon = SdlCursor("text")
internal actual val pointerIconHand: PointerIcon = SdlCursor("hand")
