package com.compose.desktop.native

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import sdl3.SDL_GetBasePath
import kotlinx.cinterop.toKString

// ==================
// MARK: composeResources file IO
// ==================
// Resources are bundled next to the executable under composeResources/ by the
// demo's Gradle Copy task (same trick as the font). At runtime we resolve them
// against SDL_GetBasePath(), which points at the executable's directory.

/* Absolute path to a bundled resource, or null if the base path is unknown. */
@OptIn(ExperimentalForeignApi::class)
fun composeResourceFullPath(inRelativePath: String): String? {
	val vBase = SDL_GetBasePath()?.toKString() ?: return null
	if (vBase.isEmpty()) return null
	return vBase + "composeResources/" + inRelativePath
}

/* Reads a bundled resource's raw bytes via stdio (portable across mac / Linux /
   mingw — long widths differ, so all C integer args go through .convert()).
   Returns null when the file is missing or empty. */
@OptIn(ExperimentalForeignApi::class)
fun loadComposeResourceBytes(inRelativePath: String): ByteArray? {
	val vPath = composeResourceFullPath(inRelativePath) ?: return null
	val vFile = fopen(vPath, "rb") ?: return null
	try {
		fseek(vFile, 0.convert(), SEEK_END)
		val vSize: Long = ftell(vFile).convert()
		rewind(vFile)
		if (vSize <= 0L) return null
		val vLen = vSize.toInt()
		val vBytes = ByteArray(vLen)
		vBytes.usePinned { vPinned ->
			fread(vPinned.addressOf(0), 1.convert(), vLen.convert(), vFile)
		}
		return vBytes
	} finally {
		fclose(vFile)
	}
}
