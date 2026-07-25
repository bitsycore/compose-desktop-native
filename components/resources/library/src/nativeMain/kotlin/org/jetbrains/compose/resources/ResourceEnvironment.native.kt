@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import com.compose.sdl.res.preferredLocaleProvider
import com.compose.sdl.res.systemThemeIsDarkProvider

// ==================
// MARK: ResourceEnvironment — platform-env-seam actual
// ==================

/** Non-composable system environment for qualifier resolution (values-fr,
   drawable-dark, …): locale + theme come from the platform-env seams installed
   by the SDL layer, so :components-resources carries no dependency on the sdl3
   cinterop. Density is reported as 1f — under this port's Option-B density flow
   layout runs in physical pixels and drawables are bundled at a single density,
   so the mdpi bucket is always the right one. The COMPOSABLE path
   (rememberResourceEnvironment) doesn't use this: it reads LocalDensity /
   isSystemInDarkTheme from the composition. */
internal actual fun getSystemEnvironment(): ResourceEnvironment {
	val (vLanguage, vRegion) = preferredLocaleProvider?.invoke() ?: ("" to "")
	val vDark = systemThemeIsDarkProvider?.invoke() ?: false
	return ResourceEnvironment(
		language = LanguageQualifier(vLanguage),
		// SDL locales carry language+country only — no script subtag.
		script = ScriptQualifier(""),
		region = RegionQualifier(vRegion),
		theme = ThemeQualifier.selectByValue(isDark = vDark),
		density = DensityQualifier.selectByDensity(1f),
	)
}
