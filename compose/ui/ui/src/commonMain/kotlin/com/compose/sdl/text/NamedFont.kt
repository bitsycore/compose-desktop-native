package com.compose.sdl.text

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// ==================
// MARK: NamedFont — project name-based Font
// ==================

/**
 * A project-specific [Font] that carries a family name rather than a resource id / bytes.
 * The SDL3 / Skia text renderer looks up the actual bytes at draw time via
 * [com.compose.sdl.text.TextMeasurer]'s registered font table.
 *
 * Upstream's `FontFamily.Named` project extension is retired — vendored `FontFamily`
 * is a `sealed class` whose only `FontListFontFamily` / `GenericFontFamily` /
 * `LoadedFontFamily` branches don't accept project subclasses. Instead, we route
 * name-based lookups through the standard `Font → FontFamily` chain: pass
 * `namedFontFamily("noto-mono")` (returns a `FontListFontFamily`) as
 * `TextStyle.fontFamily`, and the renderer extracts the family name via
 * [FontFamily.projectFontName].
 */
class NamedFont(
	val name: String,
	override val weight: FontWeight = FontWeight.Normal,
	override val style: FontStyle = FontStyle.Normal,
	val variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) : Font {
	override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking

	override fun equals(other: Any?): Boolean =
		other is NamedFont && other.name == name && other.weight == weight && other.style == style

	override fun hashCode(): Int {
		var h = name.hashCode()
		h = 31 * h + weight.hashCode()
		h = 31 * h + style.hashCode()
		return h
	}

	override fun toString(): String = "NamedFont(name=$name, weight=$weight, style=$style)"
}

/** Convenience factory — the vendored `FontFamily(vararg fonts)` factory wraps into a
 *  `FontListFontFamily`. Replaces the deleted project extension `FontFamily.Named(name)`. */
fun namedFontFamily(
	name: String,
	weight: FontWeight = FontWeight.Normal,
	style: FontStyle = FontStyle.Normal,
): FontFamily = FontFamily(NamedFont(name, weight, style))

/**
 * Extract the renderer font-registry name for a `FontFamily`:
 *   * a [namedFontFamily] (`FontListFontFamily` whose first font is a [NamedFont]) → its name;
 *   * a generic `FontFamily.Monospace` / `Serif` / `Cursive` → `"generic:<name>"`, which the
 *     renderer resolves to the bundled generic font (registerGenericFonts), falling back to the
 *     default font when that generic isn't bundled;
 *   * everything else (`Default`, `SansSerif`, `LoadedFontFamily`, resource-backed lists) → `null`
 *     (the default font).
 */
fun FontFamily?.projectFontName(): String? = when (this) {
	is FontListFontFamily -> (firstOrNull() as? NamedFont)?.name
	is androidx.compose.ui.text.font.GenericFontFamily ->
		if (name == "sans-serif") null else "generic:$name"
	else -> null
}
