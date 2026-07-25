package com.compose.sdl.text.font

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver

// ==================
// MARK: projectFontFamilyResolver — native actual
// ==================

/** Actual for `expect val projectFontFamilyResolver` — shared no-op resolver
 *  used by `LocalFontFamilyResolver`'s composition-local default. Backed by
 *  the project [androidx.compose.ui.text.font.createFontFamilyResolver] factory. */
actual val projectFontFamilyResolver: FontFamily.Resolver = createFontFamilyResolver()
