package com.compose.desktop.native.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: IconFontIcon — codepoint-based Icon rendered via a text glyph
// ==================

/* Renders a single Unicode codepoint from a named IconFont (Material Symbols /
   any glyph font registered via IconFont.registerIcon) at the given size.

   Lived in `androidx.compose.material.Icon` until :material was retired. Moved
   here (project-specific package) because it's a project-only extension —
   upstream Compose Icon takes only Painter / ImageVector, never a codepoint,
   and the icon-font pipeline is a project stand-in for icon-font Compose
   Multiplatform never shipped.

   The four Material Symbols variable-font axes (fill / weight / grade /
   opticalSize) come in via `fontVariationSettings`; use `MaterialIconAxes(…)`
   to build the list. Skia honours the axes; SDL3_ttf 3.2 ignores them. */
@Composable
fun IconFontIcon(
	codepoint: Int,
	fontFamily: String,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	tint: Color = IconDefaults.LocalContentColor,
	size: Dp = IconDefaults.DefaultIconSize,
	fontVariationSettings: List<FontVariation.Setting>? = null,
) {
	Box(modifier = modifier.size(size)) {
		com.compose.desktop.native.text.IconText(
			text = codepointToString(codepoint),
			fontFamily = fontFamily,
			color = tint,
			fontSize = size.value.sp,
			fontVariationSettings = fontVariationSettings,
		)
	}
}

// ==================
// MARK: MaterialIconAxes
// ==================

/* Convenience builder for Material Symbols variable-font axes.

   - fill        : 0..1   — FILL (0 = outlined, 1 = filled)
   - weight      : 100..700 — wght
   - grade       : -25..200 — GRAD (subtle weight nudge without footprint change)
   - opticalSize : 20..48 — opsz (glyph design size)

   Defaults match the variable font's default position, so `MaterialIconAxes()`
   returns an empty list and the renderer skips the typeface-clone path — only
   icons that request a non-default axis pay the clone cost. */
fun MaterialIconAxes(
	fill: Float = MaterialIconAxisDefaults.Fill,
	weight: Int = MaterialIconAxisDefaults.Weight,
	grade: Int = MaterialIconAxisDefaults.Grade,
	opticalSize: Int = MaterialIconAxisDefaults.OpticalSize,
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

/* Default-position values for the four Material Symbols axes — same as the
   variable font ships at. */
object MaterialIconAxisDefaults {
	const val Fill: Float = 0f
	const val Weight: Int = 400
	const val Grade: Int = 0
	const val OpticalSize: Int = 24
}

object IconDefaults {
	val DefaultIconSize: Dp = 24.dp
	/* Default tint when no explicit tint is provided. Tracks Compose's
	   LocalContentColor — wired to onSurface in the dark theme. */
	val LocalContentColor: Color = Color.White
}

/* Converts a Unicode codepoint to a String, handling supplementary-plane
   codepoints (≥0x10000) via UTF-16 surrogate pairs. */
internal fun codepointToString(inCodepoint: Int): String {
	if (inCodepoint < 0x10000) return Char(inCodepoint).toString()
	val vAdj = inCodepoint - 0x10000
	val vHigh = (0xD800 or (vAdj shr 10)).toChar()
	val vLow = (0xDC00 or (vAdj and 0x3FF)).toChar()
	return charArrayOf(vHigh, vLow).concatToString()
}
