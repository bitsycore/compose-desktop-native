package com.compose.sdl

import kotlinx.cinterop.*
import platform.posix.memcpy
import sdl3.SDL_CreateSurface
import sdl3.SDL_DestroySurface
import sdl3.SDL_GetSystemTheme
import sdl3.SDL_PIXELFORMAT_RGBA32
import sdl3.SDL_SystemTheme
import sdl3.SDL_Surface

// ==================
// MARK: Window icon — theme-aware, core-SDL only
// ==================
// The runtime window / taskbar icon is built entirely from CORE SDL — no
// SDL3_image or Skia decode — so it works identically on every renderer and
// every target (core `sdl3` is linked on all of them; the image decoders are
// not). Icons are bundled as pre-decoded RGBA blobs (produced by the bridge
// plugin's icon {} packaging or scripts/make-app-icon.py): an 8-byte little-endian header
// [width u32][height u32] followed by width*height*4 straight-alpha RGBA bytes —
// the exact memory layout of SDL_PIXELFORMAT_RGBA32, so filling the surface is a
// per-row memcpy with no conversion.

/* True when the OS reports a dark system theme. UNKNOWN and LIGHT both read as
   "not dark", so the light icon is the default. */
@OptIn(ExperimentalForeignApi::class)
internal fun systemPrefersDarkTheme(): Boolean =
	SDL_GetSystemTheme() == SDL_SystemTheme.SDL_SYSTEM_THEME_DARK

/* Decodes one .rgba blob into a fresh SDL_Surface that OWNS its pixels (so it is
   safe to free once SDL_SetWindowIcon has copied it). Null on a malformed or
   truncated blob, or an allocation failure. */
@OptIn(ExperimentalForeignApi::class)
internal fun iconSurfaceFromRgbaBlob(inBytes: ByteArray): CPointer<SDL_Surface>? {
	if (inBytes.size < 8) return null
	val vW = le32(inBytes, 0)
	val vH = le32(inBytes, 4)
	if (vW <= 0 || vH <= 0) return null
	if (inBytes.size < 8 + vW * vH * 4) return null

	val vSurface = SDL_CreateSurface(vW, vH, SDL_PIXELFORMAT_RGBA32) ?: return null
	val vPixels = vSurface.pointed.pixels?.reinterpret<ByteVar>() ?: run {
		SDL_DestroySurface(vSurface)
		return null
	}
	val vDstPitch = vSurface.pointed.pitch
	val vSrcPitch = vW * 4
	inBytes.usePinned { vPin ->
		for (vRow in 0 until vH) {
			memcpy(
				vPixels + (vRow.toLong() * vDstPitch),
				vPin.addressOf(8 + vRow * vSrcPitch),
				vSrcPitch.convert(),
			)
		}
	}
	return vSurface
}

private fun le32(inBuf: ByteArray, inOff: Int): Int =
	(inBuf[inOff].toInt() and 0xFF) or
	((inBuf[inOff + 1].toInt() and 0xFF) shl 8) or
	((inBuf[inOff + 2].toInt() and 0xFF) shl 16) or
	((inBuf[inOff + 3].toInt() and 0xFF) shl 24)
