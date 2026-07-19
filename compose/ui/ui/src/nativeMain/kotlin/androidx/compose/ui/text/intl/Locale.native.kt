package androidx.compose.ui.text.intl

import androidx.compose.runtime.Immutable

// ==================
// MARK: Locale native actual
// ==================

/*
 * Cross-platform native actual for `expect class Locale`. We don't have
 * NSLocale on linux/windows and don't want to introduce platform-specific
 * actuals for each leg, so we use a plain BCP47 language tag parser
 * (language[-script][-region]). Good enough for downstream text style
 * APIs that only need to roundtrip the tag — no actual locale-aware
 * collation / character direction is wired through yet.
 *
 * `Locale.current` reads the OS preferred locale via SDL (see
 * com.compose.sdl.text.systemPreferredLocaleTags), which is what keys Material
 * 3 string translation selection; it falls back to en-US before SDL init.
 *
 * If/when proper RTL detection is needed, plug a `Locale.isRtl()` actual
 * (which is a separate expect on skikoMain — we leave that one
 * deferred since we don't render RTL text yet either).
 */
@Immutable
actual class Locale actual constructor(languageTag: String) {

	private val parts: List<String> = languageTag.split('-', '_')

	actual val language: String get() = parts.getOrNull(0) ?: ""
	actual val script: String get() = parts.getOrNull(1)?.takeIf { it.length == 4 } ?: ""
	// Region is a subtag AFTER the language (2-letter alpha or 3-digit UN M.49);
	// drop(1) so a single-subtag tag ("en") or a 3-letter language ("fil") doesn't
	// report itself as the region.
	actual val region: String get() = parts.drop(1).lastOrNull { it.length == 2 || it.length == 3 } ?: ""

	private val tag: String = buildString {
		val vLang = language
		if (vLang.isNotEmpty()) {
			append(vLang)
			val vScript = script
			if (vScript.isNotEmpty()) { append('-'); append(vScript) }
			val vRegion = region
			if (vRegion.isNotEmpty() && vRegion != vLang) { append('-'); append(vRegion) }
		} else {
			append(languageTag)
		}
	}

	actual fun toLanguageTag(): String = tag

	actual override fun equals(other: Any?): Boolean =
		other is Locale && other.tag == tag

	actual override fun hashCode(): Int = tag.hashCode()

	actual override fun toString(): String = tag

	actual companion object {
		actual val current: Locale get() = systemCurrentLocale()
	}
}

// The OS's most-preferred locale (SDL); en-US before SDL init or on a host that
// reports none. Cheap: the tag list is cached after the first non-empty read.
private fun systemCurrentLocale(): Locale =
	com.compose.sdl.text.systemPreferredLocaleTags().firstOrNull()?.let(::Locale) ?: Locale("en-US")
