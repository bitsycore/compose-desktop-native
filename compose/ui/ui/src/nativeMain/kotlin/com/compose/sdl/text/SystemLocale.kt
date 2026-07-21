package com.compose.sdl.text

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import sdl3.SDL_GetPreferredLocales
import sdl3.SDL_free

// ==================
// MARK: System locale (SDL)
// ==================

/**
 * The OS preferred-locale list as BCP47 tags (language[-REGION]), ordered by
 * preference, read via SDL_GetPreferredLocales(). Backs Locale.current and
 * LocaleList.current (androidx.compose.ui.text.intl), which in turn key Material
 * 3 string translation selection.
 *
 * SDL exposes language + country only (no script subtag). The result is cached
 * after the first NON-EMPTY read: the list is stable for a session, and SDL
 * requires its subsystem initialized, so a pre-init read returns empty and is
 * retried rather than cached. Runtime locale changes (SDL_EVENT_LOCALE_CHANGED)
 * are not reflected — matching upstream, whose Locale.current is not reactive.
 */

private var cachedTags: List<String>? = null

// OS preferred locales as BCP47 tags, most-preferred first; empty before SDL init.
internal fun systemPreferredLocaleTags(): List<String> {
	cachedTags?.let { return it }
	val tags = readSystemPreferredLocaleTags()
	if (tags.isNotEmpty()) cachedTags = tags
	return tags
}

@OptIn(ExperimentalForeignApi::class)
private fun readSystemPreferredLocaleTags(): List<String> {
	val tags = mutableListOf<String>()
	memScoped {
		val count = alloc<IntVar>()
		val locales = SDL_GetPreferredLocales(count.ptr) ?: return@memScoped
		for (i in 0 until count.value) {
			val locale = locales[i]?.pointed ?: continue
			val language = locale.language?.toKString().orEmpty()
			if (language.isEmpty()) continue
			val country = locale.country?.toKString().orEmpty()
			tags += if (country.isEmpty()) language else "$language-$country"
		}
		SDL_free(locales)
	}
	return tags
}
