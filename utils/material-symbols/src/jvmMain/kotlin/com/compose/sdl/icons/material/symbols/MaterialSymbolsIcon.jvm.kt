package com.compose.sdl.icons.material.symbols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Font as SkFont
import org.jetbrains.skia.FontVariation as SkFontVariation
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Typeface as SkTypeface

// ==================
// MARK: renderMaterialSymbol — JVM actual (Skiko direct)
// ==================

/** Draws the glyph straight through Skiko (Compose Desktop's rendering
   backend): the style's variable font is loaded once, Typeface.makeClone
   instantiates the FILL / wght / GRAD / opsz axes per combination, and the
   glyph is drawn as a TextLine on the native Skia canvas.

   Deliberately NOT the upstream Font(variationSettings) + BasicText route:
   the published desktop artifacts ignore variationSettings at typeface load,
   and even where they don't, FontCache keys typefaces by identity/weight/
   style WITHOUT the variation settings — every axes combination of the same
   resource collapses onto the first one loaded. Skiko-direct sidesteps both.

   The app supplies the font on the JVM classpath at the style's
   DefaultResourcePath (see :demo's jvmProcessResources wiring). */

private val baseTypefaces = HashMap<String, SkTypeface?>()
private val variantTypefaces = HashMap<String, SkTypeface>()

/** The style's font as loaded from the classpath (no axes applied), null when
   the app didn't bundle it — warned once, icon renders blank. */
private fun baseTypeface(style: MaterialSymbolsIcon): SkTypeface? {
    if (style.Family in baseTypefaces) return baseTypefaces[style.Family]
    val bytes = MaterialSymbolsIcon::class.java.classLoader
        .getResourceAsStream(style.DefaultResourcePath)?.use { it.readBytes() }
    val typeface = bytes?.let { FontMgr.default.makeFromData(Data.makeFromBytes(it)) }
    if (typeface == null) {
        println(
            "${style.Family}: font missing at \"${style.DefaultResourcePath}\" on the JVM classpath. " +
                    "Add the font to your app's jvm resources."
        )
    }
    baseTypefaces[style.Family] = typeface
    return typeface
}

/** One clone per (style, axes) combination; the default axes use the font's
   own default position, no clone needed. Fill is quantised to 1/100 steps so
   ANIMATED fill values (arbitrary floats every frame) hit a bounded set of
   cached clones instead of growing the cache forever. */
private fun variantTypeface(
    style: MaterialSymbolsIcon,
    fill: Float,
    weight: Int,
    grade: Int,
    opticalSize: Int,
): SkTypeface? {
    val base = baseTypeface(style) ?: return null
    @Suppress("NAME_SHADOWING")
    val fill = kotlin.math.round(fill * 100f) / 100f
    if (fill == MaterialIconAxisDefaults.Fill &&
        weight == MaterialIconAxisDefaults.Weight &&
        grade == MaterialIconAxisDefaults.Grade &&
        opticalSize == MaterialIconAxisDefaults.OpticalSize
    ) return base
    val key = "${style.Family}/$fill/$weight/$grade/$opticalSize"
    return variantTypefaces.getOrPut(key) {
        base.makeClone(
            arrayOf(
                SkFontVariation("FILL", fill),
                SkFontVariation("wght", weight.toFloat()),
                SkFontVariation("GRAD", grade.toFloat()),
                SkFontVariation("opsz", opticalSize.toFloat()),
            )
        )
    }
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
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            }
        } else Modifier
    Box(
        modifier
            .size(size)
            .then(semanticsModifier)
            .drawWithCache {
                val typeface = variantTypeface(style, fill, weight, grade, opticalSize)
                if (typeface == null) {
                    onDrawBehind {}
                } else {
                    val skFont = SkFont(typeface, this.size.height)
                    val line = TextLine.make(codepointToString(codepoint), skFont)
                    val paint = SkPaint().apply {
                        color = tint.toArgb()
                        isAntiAlias = true
                    }
                    // Center the glyph: x from the measured advance, baseline
                    // from the line's ascent/descent (ascent is negative).
                    val x = (this.size.width - line.width) / 2f
                    val y = this.size.height / 2f - (line.ascent + line.descent) / 2f
                    onDrawBehind {
                        drawIntoCanvas { it.nativeCanvas.drawTextLine(line, x, y, paint) }
                    }
                }
            }
    )
}
