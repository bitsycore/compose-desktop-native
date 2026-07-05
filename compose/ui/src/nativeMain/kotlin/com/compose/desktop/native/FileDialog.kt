package com.compose.desktop.native

import kotlinx.cinterop.*
import sdl3.SDL_ShowOpenFileDialog
import sdl3.SDL_ShowSaveFileDialog

// ==================
// MARK: Native file dialogs (SDL3)
// ==================

/* Wraps SDL3's native "Save As" / "Open" dialogs. SDL runs the callback on the
   thread that pumps its events — i.e. the compose main-loop thread — so the
   result handler may safely touch snapshot state (the recomposer picks it up on
   the next frame).

   The C callback can't capture Kotlin state, so the result handler is parked in
   a StableRef passed as the dialog's `userdata` and disposed inside the callback. */
private val fDialogCallback = staticCFunction { inUserData: COpaquePointer?, inFileList: CPointer<CPointerVar<ByteVar>>?, inFilter: Int ->
    inFilter.hashCode() // unused (selected filter index)
    val vRef = inUserData?.asStableRef<(String?) -> Unit>() ?: return@staticCFunction
    val vHandler = vRef.get()
    vRef.dispose()
    // filelist is NULL on cancel/error, else a NULL-terminated array of paths;
    // we surface the first selection only.
    val vPath = inFileList?.get(0)?.toKString()
    vHandler(vPath)
}

/* Show the OS "Save As" dialog. inOnResult receives the chosen path, or null if
   the user cancelled. inDefaultName seeds the suggested file name / location. */
fun showSaveFileDialog(inDefaultName: String? = null, inOnResult: (String?) -> Unit) {
    val vRef = StableRef.create(inOnResult)
    SDL_ShowSaveFileDialog(
        fDialogCallback,
        vRef.asCPointer(),
        null,            // parent window (null = unparented)
        null,            // no file-type filters
        0,
        inDefaultName,   // suggested location / name
    )
}

/* Show the OS "Open" dialog (single selection). inOnResult receives the chosen
   path, or null on cancel. */
fun showOpenFileDialog(inOnResult: (String?) -> Unit) {
    val vRef = StableRef.create(inOnResult)
    SDL_ShowOpenFileDialog(
        fDialogCallback,
        vRef.asCPointer(),
        null,    // parent window
        null,    // filters
        0,
        null,    // default location
        false,   // allow_many
    )
}
