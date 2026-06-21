package androidx.compose.material

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: Icon
// ==================

/* Renders an icon at the given size. Three input forms:

   - Painter: a bundled drawable (PNG/JPG/SVG/Android-vector). Tint is not
     applied — the renderer has no ColorFilter primitive yet, so the painter
     is drawn as-is. Use Image() directly if you need ContentScale control.

   - codepoint + fontFamily: a single Unicode codepoint rendered as text in
     the named IconFont (e.g. MaterialSymbols.Home / "material-symbols-
     outlined"). This is the path used by Material Symbols modules and
     respects `tint`.

   - imageVector: not implemented in this subset. Use a Painter for SVG
     instead (drop the .svg / android-<vector>.xml into composeResources/
     drawable and access via Res.drawable.*).

   The DefaultIconSize (24.dp) matches Material 1. Override via Modifier
   .size(...) when you want a different display size. */
@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = IconDefaults.LocalContentColor,
) {
    Box(modifier = modifier.size(IconDefaults.DefaultIconSize)) {
        // tint currently ignored for Painter icons — see file header.
        Image(painter = painter, contentDescription = contentDescription, modifier = Modifier.size(IconDefaults.DefaultIconSize))
    }
}

@Composable
fun Icon(
    codepoint: Int,
    fontFamily: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = IconDefaults.LocalContentColor,
    size: Dp = IconDefaults.DefaultIconSize,
    fontVariationSettings: List<FontVariation>? = null,
) {
    Box(modifier = modifier.size(size)) {
        Text(
            text = codepointToString(codepoint),
            color = tint,
            fontSize = Sp(size.value),
            fontFamily = fontFamily,
            fontVariationSettings = fontVariationSettings,
        )
    }
}

// ==================
// MARK: MaterialIconAxes
// ==================

/* Convenience builder for Material Symbols variable-font axes. Each
   parameter maps to one OpenType axis on the official Material Symbols
   variable font:

   - fill   : 0..1   — FILL (0 = outlined, 1 = filled)
   - weight : 100..700 — wght (400 = regular, 700 = bold)
   - grade  : -25..200 — GRAD (subtle weight nudge without changing footprint;
                                negative = thinner, positive = heavier; tune for
                                emphasis or light-on-dark legibility)
   - opticalSize : 20..48 — opsz (size the glyph was designed for; pick the
                                  closest value to the rendered size)

   Defaults match the variable font's default position (the values you get
   when no axis is set), so `MaterialIconAxes()` returns an empty list and
   the renderer skips the typeface-clone path entirely — only icons that
   request a non-default axis pay the clone cost.

   Use as:
       Icon(
           codepoint = MaterialSymbols.Home,
           fontFamily = MaterialSymbolsOutlined.Family,
           fontVariationSettings = MaterialIconAxes(fill = 1f, weight = 700),
       )

   Skia honours all axes; SDL3_ttf 3.2 ignores them. */
fun MaterialIconAxes(
    fill: Float = MaterialIconAxisDefaults.Fill,
    weight: Int = MaterialIconAxisDefaults.Weight,
    grade: Int = MaterialIconAxisDefaults.Grade,
    opticalSize: Int = MaterialIconAxisDefaults.OpticalSize,
): List<FontVariation> {
    // All four at default → empty list → renderer reuses the base typeface
    // (no makeClone). Only the axes the caller actually moved off-default
    // would need cloning, but variable fonts treat each axis as independent
    // anyway, so packing all four when ANY one differs is fine and keeps
    // the cache key stable across paths that set different subsets.
    if (fill == MaterialIconAxisDefaults.Fill &&
        weight == MaterialIconAxisDefaults.Weight &&
        grade == MaterialIconAxisDefaults.Grade &&
        opticalSize == MaterialIconAxisDefaults.OpticalSize
    ) {
        return emptyList()
    }
    return listOf(
        FontVariation.Fill(fill),
        FontVariation.Weight(weight),
        FontVariation.Grade(grade),
        FontVariation.OpticalSize(opticalSize),
    )
}

/* Default-position values for the four Material Symbols axes — same as the
   variable font ships at. Centralised so MaterialIconAxes and the style
   modules' invoke composables stay in sync. */
object MaterialIconAxisDefaults {
    const val Fill: Float = 0f
    const val Weight: Int = 400
    const val Grade: Int = 0
    const val OpticalSize: Int = 24
}

/* Converts a Unicode codepoint to a String, handling supplementary-plane
   codepoints (≥0x10000) via UTF-16 surrogate pairs. Material Symbols
   icons all live in the BMP (single Char), but Material Symbols Sharp/Outlined
   Pro and any future icon font may use the supplementary plane. */
internal fun codepointToString(inCodepoint: Int): String {
    if (inCodepoint < 0x10000) return Char(inCodepoint).toString()
    val vAdj = inCodepoint - 0x10000
    val vHigh = (0xD800 or (vAdj shr 10)).toChar()
    val vLow = (0xDC00 or (vAdj and 0x3FF)).toChar()
    return charArrayOf(vHigh, vLow).concatToString()
}

object IconDefaults {
    val DefaultIconSize: Dp = 24.dp
    /* Default tint when no explicit tint is provided. Tracks Compose's
       LocalContentColor — wired to onSurface in the dark theme. */
    val LocalContentColor: Color = Color.White
}
