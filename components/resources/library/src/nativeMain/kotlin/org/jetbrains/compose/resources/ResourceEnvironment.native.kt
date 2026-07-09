@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi

import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import sdl3.SDL_GetPreferredLocales
import sdl3.SDL_GetSystemTheme
import sdl3.SDL_SystemTheme
import sdl3.SDL_free

// ==================
// MARK: ResourceEnvironment — SDL actual
// ==================

/* Non-composable system environment for qualifier resolution (values-fr,
   drawable-dark, …): locale from SDL_GetPreferredLocales, theme from
   SDL_GetSystemTheme. Density is reported as 1f — under this port's Option-B
   density flow layout runs in physical pixels and drawables are bundled at a
   single density, so the mdpi bucket is always the right one. The COMPOSABLE
   path (rememberResourceEnvironment) doesn't use this: it reads
   LocalDensity / isSystemInDarkTheme from the composition. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun getSystemEnvironment(): ResourceEnvironment {
	var vLanguage = ""
	var vRegion = ""
	memScoped {
		val vCount = alloc<IntVar>()
		val vLocales = SDL_GetPreferredLocales(vCount.ptr)
		if (vLocales != null && vCount.value > 0) {
			val vFirst = vLocales[0]?.pointed
			vLanguage = vFirst?.language?.toKString() ?: ""
			vRegion = vFirst?.country?.toKString() ?: ""
			SDL_free(vLocales)
		}
	}
	val vDark = SDL_GetSystemTheme() == SDL_SystemTheme.SDL_SYSTEM_THEME_DARK
	return ResourceEnvironment(
		language = LanguageQualifier(vLanguage),
		region = RegionQualifier(vRegion),
		theme = ThemeQualifier.selectByValue(isDark = vDark),
		density = DensityQualifier.selectByDensity(1f),
	)
}
