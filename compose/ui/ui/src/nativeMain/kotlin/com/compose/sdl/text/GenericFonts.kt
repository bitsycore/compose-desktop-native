package com.compose.sdl.text

import com.compose.sdl.icons.IconFont
import com.compose.sdl.loadComposeResourceBytes

// ==================
// MARK: Generic font families (FontFamily.Monospace / Serif / Cursive)
// ==================

/**
 * Registers the bundled fonts that back the generic FontFamily.* values, under the
 * "generic:<name>" keys that FontFamily.projectFontName() returns. Today only
 * Monospace has a bundled font (NotoSansMono, fetched by the app's downloadNotoFonts task);
 * an app opts in by bundling font/NotoSansMono.ttf into its data.kres. When a generic
 * isn't bundled the lookup misses and the renderer falls back to the default font, so
 * FontFamily.Monospace simply renders as the default sans until the mono font is bundled.
 *
 * Idempotent; called from ComposeWindow.installGlobals (data.kres is loadable by then).
 */
private var fRegistered = false

fun registerGenericFonts() {
	if (fRegistered) return
	fRegistered = true
	loadComposeResourceBytes("font/NotoSansMono.ttf")?.let { IconFont.register("generic:monospace", it) }
}
