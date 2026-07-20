package com.compose.sdl

// ==================
// MARK: AppWindowIcon
// ==================

/**
 * The window / taskbar icon for a [Window] / [nativeComposeWindow], resolved
 * against the OS theme: the [dark] set is used when the system is in dark mode
 * (and it is non-empty), otherwise [light].
 *
 * Each list holds data.kres resource paths of PRE-DECODED `.rgba` icon blobs
 * (produced by `scripts/make-app-icon.py rgba`, i.e. an 8-byte
 * `[width u32-le][height u32-le]` header + straight-alpha RGBA pixels). List
 * one path per size you bundle; the largest becomes the base and the rest are
 * attached as alternate resolutions SDL chooses from (title bar vs taskbar vs
 * Alt-Tab), so the icon stays crisp at every size.
 *
 * On Windows the runtime icon set here complements the icon EMBEDDED in the
 * `.exe` (Explorer / pinned taskbar) — see the app / bridge-plugin Gradle setup.
 */
class AppWindowIcon(
	val light: List<String>,
	val dark: List<String> = light,
)
