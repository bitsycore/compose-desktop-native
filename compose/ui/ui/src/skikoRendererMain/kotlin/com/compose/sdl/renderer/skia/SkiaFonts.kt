package com.compose.sdl.renderer.skia

import androidx.compose.ui.text.font.FontVariation as ComposeFontVariation
import com.compose.sdl.icons.IconFont
import com.compose.sdl.loadComposeResourceBytes
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
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

	private val fontMgr = FontMgr.default
	private val provider = TypefaceFontProvider()

	/** Bundled default (NotoSans from data.kres); the fallback for every unresolved family. */
	val defaultTypeface: Typeface? =
		loadComposeResourceBytes("font/NotoSans.ttf")
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
			if (bytes != null) fontMgr.makeFromData(Data.makeFromBytes(bytes), 0) else defaultTypeface
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
