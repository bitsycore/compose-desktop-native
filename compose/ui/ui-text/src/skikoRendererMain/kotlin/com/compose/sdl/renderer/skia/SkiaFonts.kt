package com.compose.sdl.renderer.skia

import androidx.compose.ui.text.font.FontVariation as ComposeFontVariation
import com.compose.sdl.icons.IconFont
import com.compose.sdl.res.composeResourceReader
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle as SkiaFontStyle
import org.jetbrains.skia.FontVariation as SkiaFontVariation
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.TypefaceFontProvider

// ==================
// MARK: SkiaFonts — font-resolution bridge for the skiko paragraph engine
// ==================

/**
 * Turns the port's `(family-name, variable-axis)` font model into skiko
 * [Typeface]s for the paragraph engine. Families come from
 * [com.compose.sdl.text.NamedFont.projectFontName] (a bundled default, an
 * [IconFont]-registered text/icon family, or `generic:*`); variable axes
 * (Material Symbols `FILL`/`wght`/… and paragraph `FontWeight`→`wght`) are
 * applied via [Typeface.makeClone].
 *
 * Every resolved typeface is registered in a [TypefaceFontProvider] under a
 * UNIQUE alias, and [resolve] hands that alias back so the caller sets both
 * `TextStyle.typeface` AND `TextStyle.fontFamilies = [alias]`. That matters:
 * skiko's shaper maps codepoints→glyphs through `fontFamilies` (the
 * FontCollection), so an icon font MUST be reachable by its alias — otherwise a
 * bare `typeface` is ignored and private-use icon codepoints render as tofu.
 * (Mirrors upstream FontCache: register alias + set fontFamilies + typeface.)
 */
internal object SkiaFonts {
	private const val DEFAULT_ALIAS = "Noto Sans"
	private const val GENERIC_PREFIX = "generic:"

	private val fontMgr = FontMgr.default
	private val provider = TypefaceFontProvider()

	/** Bundled default (NotoSans from data.kres); the fallback for every unresolved family. */
	val defaultTypeface: Typeface? =
		composeResourceReader?.invoke("font/NotoSans.ttf")
			?.let { fontMgr.makeFromData(Data.makeFromBytes(it), 0) }
			?.also { provider.registerTypeface(it, DEFAULT_ALIAS) }

	/** Shared collection: the alias provider first (bundled + icon + varied fonts),
	   then the system FontMgr for glyph fallback (CJK/emoji where present). */
	val fontCollection: FontCollection = FontCollection().apply {
		setDefaultFontManager(fontMgr)
		setAssetFontManager(provider)
	}

	// family name (null = default) -> base typeface
	private val baseCache = mutableMapOf<String?, Typeface?>()
	// resolve key -> (typeface, registered alias)
	private val resolveCache = mutableMapOf<String, Pair<Typeface?, String>>()
	private val registered = mutableSetOf(DEFAULT_ALIAS)

	private fun baseTypeface(family: String?): Typeface? =
		baseCache.getOrPut(family) {
			val bytes = family?.let { IconFont.bytesFor(it) }
			when {
				bytes != null -> fontMgr.makeFromData(Data.makeFromBytes(bytes), 0)
				// Generic family (serif / cursive / …) not bundled: resolve through the
				// per-OS concrete-name alias list (upstream GenericFontFamiliesMapping),
				// so `FontFamily.Serif` picks Times/Noto Serif instead of silently
				// becoming the sans-serif default. (Monospace is caught by IconFont above
				// when NotoSansMono is bundled; sans-serif maps to null → default Noto Sans.)
				family != null && family.startsWith(GENERIC_PREFIX) ->
					resolveGeneric(family.removePrefix(GENERIC_PREFIX)) ?: defaultTypeface
				// A concrete family name: resolve against the OS font set (e.g. "Arial",
				// "Times New Roman"), so a requested system font isn't silently Noto Sans.
				family != null ->
					runCatching { fontMgr.matchFamilyStyle(family, SkiaFontStyle.NORMAL) }.getOrNull()
						?: defaultTypeface
				else -> defaultTypeface
			}
		}

	/** Resolve a generic family name (serif / sans-serif / monospace / cursive) to a
	   concrete OS typeface by trying the per-OS candidate names in order. */
	private fun resolveGeneric(genericName: String): Typeface? {
		for (name in genericFamilyAliases(genericName)) {
			runCatching { fontMgr.matchFamilyStyle(name, SkiaFontStyle.NORMAL) }.getOrNull()?.let { return it }
		}
		return null
	}

	private fun variationsKey(variations: List<ComposeFontVariation.Setting>): String =
		variations.sortedBy { it.axisName }.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }

	/** Resolve a family + optional variable-axis settings to a concrete typeface AND
	   a provider alias (register-on-first-use). Set `fontFamilies = [alias]` and
	   `typeface = first` on the skiko TextStyle. */
	fun resolve(family: String?, variations: List<ComposeFontVariation.Setting>?): Pair<Typeface?, String> {
		if (family == null && variations.isNullOrEmpty()) return defaultTypeface to DEFAULT_ALIAS
		val varKey = variations?.takeUnless { it.isEmpty() }?.let { variationsKey(it) } ?: ""
		val key = "${family ?: ""}#$varKey"
		return resolveCache.getOrPut(key) {
			val base = baseTypeface(family) ?: defaultTypeface
			val typeface = if (varKey.isEmpty() || base == null) base
			else runCatching {
				base.makeClone(variations!!.map { SkiaFontVariation(it.axisName, it.toVariationValue(null)) }.toTypedArray())
			}.getOrDefault(base)
			val alias = if (family == null && varKey.isEmpty()) DEFAULT_ALIAS else "cdn-font:$key"
			if (typeface != null && alias !in registered) {
				provider.registerTypeface(typeface, alias)
				registered += alias
			}
			typeface to alias
		}
	}
}

// ==================
// MARK: Generic font family aliases (per-OS)
// ==================

/** Concrete OS family-name candidates for a generic family, tried in order.
   Mirrors upstream `GenericFontFamiliesMapping` (PlatformFont.skiko.kt); unknown
   generics get no candidates so they fall back to the bundled default. */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun genericFamilyAliases(genericName: String): List<String> = when (kotlin.native.Platform.osFamily) {
	kotlin.native.OsFamily.MACOSX -> when (genericName) {
		"sans-serif" -> listOf(".AppleSystemUIFont", "Helvetica Neue", "Helvetica")
		"serif" -> listOf(".AppleSystemUIFontSerif", "Times", "Times New Roman")
		"monospace" -> listOf(".AppleSystemUIFontMonospaced", "Menlo", "Courier")
		"cursive" -> listOf("Apple Chancery", "Snell Roundhand")
		else -> emptyList()
	}
	kotlin.native.OsFamily.WINDOWS -> when (genericName) {
		"sans-serif" -> listOf("Segoe UI", "Arial")
		"serif" -> listOf("Times New Roman")
		"monospace" -> listOf("Consolas")
		"cursive" -> listOf("Comic Sans MS")
		else -> emptyList()
	}
	else -> when (genericName) { // Linux + everything else
		"sans-serif" -> listOf("Noto Sans", "DejaVu Sans", "Arial")
		"serif" -> listOf("Noto Serif", "DejaVu Serif", "Times New Roman")
		"monospace" -> listOf("Noto Sans Mono", "DejaVu Sans Mono", "Consolas")
		"cursive" -> listOf("Comic Sans MS")
		else -> emptyList()
	}
}
