package androidx.compose.ui.text.platform

import androidx.compose.ui.text.PlatformStringDelegate
import androidx.compose.ui.text.intl.Locale

// Native actual for vendored commonMain `PlatformString.kt`.
//
// Upstream's desktop actual goes through `java.util.Locale.platformLocale`
// for locale-aware case mapping. We don't have java.util.Locale on
// linux/windows, and our project `Locale.native.kt` doesn't expose a
// platformLocale either. Use the locale-independent Kotlin stdlib
// `uppercase()` / `lowercase()` here — sufficient for the small number
// of locales we ever care about (basic Latin) and matches the way
// vendored `AnnotatedString.toUpperCase` is invoked at the renderers
// (case mapping is non-load-bearing in our text pipeline).
private class NativeStringDelegate : PlatformStringDelegate {
	override fun toUpperCase(string: String, locale: Locale): String = string.uppercase()
	override fun toLowerCase(string: String, locale: Locale): String = string.lowercase()
	override fun capitalize(string: String, locale: Locale): String =
		string.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
	override fun decapitalize(string: String, locale: Locale): String =
		string.replaceFirstChar { it.lowercase() }
}

internal actual fun ActualStringDelegate(): PlatformStringDelegate = NativeStringDelegate()
