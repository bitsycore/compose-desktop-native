package com.compose.sdl

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import sdl3.SDL_GetPrefPath
import sdl3.SDL_free

// ==================
// MARK: App data directory
// ==================

/* Returns a per-user, writable directory an app can drop its config / state
   into, creating it if needed. Wraps SDL_GetPrefPath, which resolves to the
   platform-appropriate location (e.g. %APPDATA%\inOrg\inApp\ on Windows,
   ~/Library/Application Support/inApp/ on macOS, $XDG_DATA_HOME/inApp/ on
   Linux). The returned path ends with a path separator, so just append a file
   name. Open it with okio (FileSystem.SYSTEM). Returns null if SDL can't
   provide one. */
@OptIn(ExperimentalForeignApi::class)
fun appDataDir(inOrg: String, inApp: String): String? {
    val vPtr = SDL_GetPrefPath(inOrg, inApp) ?: return null
    val vPath = vPtr.toKString()
    SDL_free(vPtr)          // SDL_GetPrefPath returns a malloc'd string we own
    return vPath
}
