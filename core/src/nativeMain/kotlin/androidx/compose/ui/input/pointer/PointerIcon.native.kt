package androidx.compose.ui.input.pointer

// ==================
// MARK: PointerIcon actuals — SDL3 cursor placeholders
// ==================

/*
 Actuals for vendored PointerIcon.kt. Each of the four canonical icons is a
 distinct marker object today; the runtime path in ComposeWindow can switch
 on identity to call SDL_SetCursor(SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_*))
 when the pointer icon service is wired up.

 TODO: install a real PointerIconService on the ComposeOwner that maps these
 icons to SDL_SYSTEM_CURSOR_* and calls SDL_SetCursor on hover — desktop-first
 cursor management via SDL3 stays in nativeMain so both renderers share it.
*/

private class SdlCursor(@Suppress("unused") val name: String) : PointerIcon

internal actual val pointerIconDefault: PointerIcon = SdlCursor("default")
internal actual val pointerIconCrosshair: PointerIcon = SdlCursor("crosshair")
internal actual val pointerIconText: PointerIcon = SdlCursor("text")
internal actual val pointerIconHand: PointerIcon = SdlCursor("hand")
