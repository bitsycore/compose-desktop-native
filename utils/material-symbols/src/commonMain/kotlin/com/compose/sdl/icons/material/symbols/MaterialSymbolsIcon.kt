@file:Suppress("PropertyName", "ConstPropertyName")

package com.compose.sdl.icons.material.symbols

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: MaterialSymbolsIcon — COMMON API
// ==================

/** The public Material Symbols surface, declared in commonMain so consumers'
   shared code (and the IDE's common analysis) resolve it directly. Rendering
   goes through the internal expect below:

     native  — the port's IconFont pipeline (:foundation IconFontIcon; the
               SDL3 / Skia renderer split is handled inside :ui — this module
               never sees it). Fonts come from data.kres.
     jvm     — upstream Compose Desktop: a classpath ResourceFont carrying the
               variable-font axes via FontVariation.Settings + BasicText.

   The app owns the font files for both stacks (data.kres Zip task on native,
   jvm resources at font/<Style>.ttf on JVM — see :demo's build file). */
sealed class MaterialSymbolsIcon {

    /** Font-family key the glyphs render under (also the install/cache key). */
    abstract val Family: String

    /** Where the app is expected to ship this style's variable font. */
    abstract val DefaultResourcePath: String

    @Composable
    operator fun invoke(
        icon: Int,
        contentDescription: String? = null,
        modifier: Modifier = Modifier,
        tint: Color = Color.Unspecified,
        size: Dp = 24.dp,
        fill: Float = MaterialIconAxisDefaults.Fill,
        weight: Int = MaterialIconAxisDefaults.Weight,
        grade: Int = MaterialIconAxisDefaults.Grade,
        opticalSize: Int = MaterialIconAxisDefaults.OpticalSize,
    ) {
        renderMaterialSymbol(
            style = this,
            codepoint = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            // Default tint mirrors upstream material3 Icon: LocalContentColor.
            tint = if (tint.isSpecified) tint else LocalContentColor.current,
            size = size,
            fill = fill,
            weight = weight,
            grade = grade,
            opticalSize = opticalSize,
        )
    }

}

object MaterialSymbolsOutlined : MaterialSymbolsIcon() {
    override val Family: String = "material-symbols-outlined"
    override val DefaultResourcePath: String = "font/MaterialSymbolsOutlined.ttf"
}

object MaterialSymbolsSharp : MaterialSymbolsIcon() {
    override val Family: String = "material-symbols-sharp"
    override val DefaultResourcePath: String = "font/MaterialSymbolsSharp.ttf"
}

object MaterialSymbolsRounded : MaterialSymbolsIcon() {
    override val Family: String = "material-symbols-rounded"
    override val DefaultResourcePath: String = "font/MaterialSymbolsRounded.ttf"
}

/** Default positions of the four Material Symbols variable-font axes. */
object MaterialIconAxisDefaults {
    const val Fill: Float = 0f
    const val Weight: Int = 400
    const val Grade: Int = 0
    const val OpticalSize: Int = 24
}

/** Per-stack glyph renderer — receives the tint already resolved. */
@Composable
internal expect fun renderMaterialSymbol(
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
)

/** Converts a Unicode codepoint to a String, handling supplementary-plane
   codepoints (≥0x10000) via UTF-16 surrogate pairs. */
internal fun codepointToString(codepoint: Int): String {
    if (codepoint < 0x10000) return Char(codepoint).toString()
    val adjusted = codepoint - 0x10000
    val high = (0xD800 or (adjusted shr 10)).toChar()
    val low = (0xDC00 or (adjusted and 0x3FF)).toChar()
    return charArrayOf(high, low).concatToString()
}
