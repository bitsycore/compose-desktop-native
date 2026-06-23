package com.compose.desktop.native

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import sdl3.SDL_OpenURL

// ==================
// MARK: Open external (URLs / file manager)
// ==================

/* Opens a URL or file:// URI with the OS's default handler (browser, file
   manager, …) via SDL_OpenURL. Returns true on success. */
@OptIn(ExperimentalForeignApi::class)
fun openUrl(inUrl: String): Boolean = SDL_OpenURL(inUrl)

/* Reveals a saved file in the OS file manager by opening its containing folder
   (Explorer on Windows, Finder on macOS, the default file manager on Linux).
   SDL has no "select the file" primitive, so we open the folder. */
fun revealInFileManager(inPath: String): Boolean {
    val vNorm = inPath.trim().replace('\\', '/')
    val vDir = vNorm.substringBeforeLast('/', "")
    val vTarget = if (vDir.isEmpty()) vNorm else vDir
    return openUrl("file:///$vTarget")
}

/* The OS file-manager's name, for menu labels ("Explorer" / "Finder" / …). */
@OptIn(ExperimentalNativeApi::class)
fun fileManagerName(): String = when (Platform.osFamily) {
    OsFamily.MACOSX -> "Finder"
    OsFamily.WINDOWS -> "Explorer"
    else -> "file manager"
}
