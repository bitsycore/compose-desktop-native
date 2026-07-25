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
 * [Typeface]s for [SkiaParagraph]. Families come from
 * [com.compose.sdl.text.NamedFont.projectFontName] (a bundled default, a
 * [IconFont]-registered text/icon family, or `generic:*`); variable axes
 * (Material Symbols `wght`/`FILL`/… and paragraph `FontWeight`→`wght`) are
 * applied via [Typeface.makeClone]. Replaces `SkiaTextRenderer`'s typeface
 * cache while keeping the same registry, so icons / monospace / custom fonts
 * resolve unchanged — only the shaping/layout engine underneath changes.
 */
internal object SkiaFonts {
	private val fontMgr = FontMgr.default

	/** Bundled default (NotoSans from data.kres); the fallback for every unresolved family. */
	val defaultTypeface: Typeface? by lazy {
		loadComposeResourceBytes("font/NotoSans.ttf")?.let {
			fontMgr.makeFromData(Data.makeFromBytes(it), 0)
		}
	}

	/** Shared collection used by skiko for glyph fallback (CJK/emoji via the system
	   FontMgr where present); the bundled NotoSans is registered as "Noto Sans". */
	val fontCollection: FontCollection by lazy {
		val provider = TypefaceFontProvider()
		defaultTypeface?.let { provider.registerTypeface(it, "Noto Sans") }
		FontCollection().apply {
			setDefaultFontManager(fontMgr)
			setAssetFontManager(provider)
		}
	}

	// family name (null = default) -> base typeface
	private val baseCache = mutableMapOf<String?, Typeface?>()
	// (family, sorted-variations key) -> axis-cloned typeface
	private val variantCache = mutableMapOf<Pair<String?, String>, Typeface?>()

	private fun baseTypeface(family: String?): Typeface? =
		baseCache.getOrPut(family) {
			val bytes = family?.let { IconFont.bytesFor(it) }
			if (bytes != null) fontMgr.makeFromData(Data.makeFromBytes(bytes), 0) else defaultTypeface
		}

	private fun variationsKey(variations: List<ComposeFontVariation.Setting>): String =
		variations.sortedBy { it.axisName }.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }

	/** Resolve a family + optional variable-axis settings to a concrete typeface,
	   falling back to the bundled default. Cloned/variant typefaces are cached. */
	fun typeface(family: String?, variations: List<ComposeFontVariation.Setting>?): Typeface? {
		if (variations.isNullOrEmpty()) return baseTypeface(family)
		return variantCache.getOrPut(family to variationsKey(variations)) {
			val base = baseTypeface(family) ?: return@getOrPut null
			runCatching {
				base.makeClone(
					variations.map { SkiaFontVariation(it.axisName, it.toVariationValue(null)) }.toTypedArray()
				)
			}.getOrDefault(base)
		}
	}
}
