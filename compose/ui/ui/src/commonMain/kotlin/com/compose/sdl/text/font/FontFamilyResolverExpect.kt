package com.compose.sdl.text.font

import androidx.compose.ui.text.font.FontFamily

// ==================
// MARK: Project font-family resolver expect
// ==================

/**
 * Project-wide shared [FontFamily.Resolver] instance. The default upstream
 * `LocalFontFamilyResolver.current` throws when read without a Provider; our
 * `LocalFontFamilyResolver` (in commonMain) reads this expect to get a no-op
 * resolver so composables that read `LocalFontFamilyResolver.current` off a
 * window (probes / tests) get a working (name-based) resolver.
 *
 * `FontFamily.Resolver` is a `sealed interface`, so anonymous / project subclass
 * impls aren't allowed — the concrete `FontFamilyResolverImpl` lives in
 * `FontFamilyResolver.kt` inside the sealed hierarchy. Native `actual` returns
 * one such instance built from a [SdlPlatformFontLoader].
 */
expect val projectFontFamilyResolver: FontFamily.Resolver
