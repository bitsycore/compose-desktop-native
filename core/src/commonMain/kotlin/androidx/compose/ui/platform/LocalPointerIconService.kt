package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.PointerIconService

// ==================
// MARK: LocalPointerIconService
// ==================

/*
 CompositionLocal for the active PointerIconService. Upstream declares this
 inside CompositionLocals.kt (which we don't vendor because it's platform-View
 heavy); ours is a standalone file so vendored `PointerIcon` can
 `currentValueOf(LocalPointerIconService)` without pulling that file.

 Default: null (no cursor management). ComposeWindow can install a real
 service that talks to SDL_SetCursor.
*/
internal val LocalPointerIconService = staticCompositionLocalOf<PointerIconService?> { null }
