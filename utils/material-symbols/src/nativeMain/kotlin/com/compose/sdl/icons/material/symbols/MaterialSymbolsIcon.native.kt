package com.compose.sdl.icons.material.symbols

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import com.compose.sdl.icons.IconFont
import com.compose.sdl.icons.IconFontIcon
import com.compose.sdl.loadComposeResourceBytes
import com.compose.sdl.text.TextRendererCapabilities

// ==================
// MARK: renderMaterialSymbol — native actual (port IconFont pipeline)
// ==================

/** Draws through :foundation's IconFontIcon; IconFont / the text renderers in
   :ui handle the SDL3-vs-Skia split, so this module stays renderer-unaware.
   The style's variable font is loaded once per family from data.kres. */

private val installedFamilies = mutableSetOf<String>()

/** Registers the style's font with IconFont on first use. Returns false (and
   warns) when the app's data.kres doesn't carry the font — the icon then
   renders as blank, same as before. */
private fun ensureInstalled(style: MaterialSymbolsIcon): Boolean {
    if (style.Family in installedFamilies) return true
    val bytes = loadComposeResourceBytes(style.DefaultResourcePath)
    if (bytes == null) {
        println(
            "${style.Family}: font missing at \"${style.DefaultResourcePath}\" in data.kres. " +
                    "Add the font to your app's resources."
        )
        return false
    }
    IconFont.registerIcon(style.Family, bytes)
    installedFamilies.add(style.Family)
    if (TextRendererCapabilities.supportsFontVariations == false) {
        println(
            "${style.Family}: the active text renderer does not support variable-font axes. " +
                    "Icons will render at the font's default position (wght=${MaterialIconAxisDefaults.Weight}, FILL=${MaterialIconAxisDefaults.Fill}, GRAD=${MaterialIconAxisDefaults.Grade}, opsz=${MaterialIconAxisDefaults.OpticalSize}). " +
                    "fill / weight / grade / opticalSize parameters are ignored."
        )
    }
    return true
}

@Composable
internal actual fun renderMaterialSymbol(
    style: MaterialSymbolsIcon,
    codepoint: Int,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    size: Dp,
    fill: Float,
    weight: Int,
    grade: Int,
    opticalSize: Int,
) {
    ensureInstalled(style)
    IconFontIcon(
        codepoint = codepoint,
        fontFamily = style.Family,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
        fontVariationSettings = materialIconAxes(fill, weight, grade, opticalSize),
    )
}

/** Axis list for IconFontIcon — empty at the defaults so the renderers skip
   the variable-font instancing path entirely. */
private fun materialIconAxes(
    fill: Float,
    weight: Int,
    grade: Int,
    opticalSize: Int,
): List<FontVariation.Setting> {
    if (fill == MaterialIconAxisDefaults.Fill &&
        weight == MaterialIconAxisDefaults.Weight &&
        grade == MaterialIconAxisDefaults.Grade &&
        opticalSize == MaterialIconAxisDefaults.OpticalSize
    ) return emptyList()
    return listOf(
        FontVariation.Setting("FILL", fill),
        FontVariation.Setting("wght", weight.toFloat()),
        FontVariation.Setting("GRAD", grade.toFloat()),
        FontVariation.Setting("opsz", opticalSize.toFloat()),
    )
}
