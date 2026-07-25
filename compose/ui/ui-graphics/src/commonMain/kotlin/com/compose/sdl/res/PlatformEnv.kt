package com.compose.sdl.res

// ==================
// MARK: Platform-environment seams
// ==================

/**
 * System-environment seams the platform layer (the SDL module) installs at
 * startup, so `:foundation` (`isSystemInDarkTheme`) and `:components-resources`
 * (locale / theme resource qualifiers) can read them WITHOUT a dependency on the
 * sdl3 cinterop — the cinterop lives in the SDL platform module, but these
 * consumers sit below it. null until installed → callers fall back to the light
 * theme / empty locale (the sensible headless default).
 */

/** True when the OS is in dark mode. Installed from SDL_GetSystemTheme. */
var systemThemeIsDarkProvider: (() -> Boolean)? = null

/** The OS preferred locale as (language, region) — e.g. ("fr", "FR"); either may
   be empty. Installed from SDL_GetPreferredLocales. */
var preferredLocaleProvider: (() -> Pair<String, String>)? = null

/** OS preferred-locale BCP47 tags (language[-REGION]), most-preferred first —
   backs androidx.compose.ui.text.intl Locale.current / LocaleList.current (in
   :ui-text). Installed from SDL_GetPreferredLocales. */
var preferredLocaleTagsProvider: (() -> List<String>)? = null
