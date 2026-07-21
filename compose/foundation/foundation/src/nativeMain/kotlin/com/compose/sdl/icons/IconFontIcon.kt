package com.compose.sdl.icons

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

/** Renders a single Unicode codepoint from a named IconFont (Material Symbols /
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
		com.compose.sdl.text.IconText(
			text = codepointToString(codepoint),
			fontFamily = fontFamily,
			color = tint,
			fontSize = size.value.sp,
			fontVariationSettings = fontVariationSettings,
		)
	}
}

object IconDefaults {
	val DefaultIconSize: Dp = 24.dp
	/** STATIC fallback tint for direct IconFontIcon use — :foundation cannot
	   read material3's LocalContentColor (module layering, same as upstream).
	   The themed default lives one layer up: MaterialSymbols<Style> defaults
	   its tint to material3's LocalContentColor.current, mirroring upstream
	   material3 Icon. Prefer passing an explicit tint when calling
	   IconFontIcon directly. */
	val LocalContentColor: Color = Color.White
}

/** Converts a Unicode codepoint to a String, handling supplementary-plane
   codepoints (≥0x10000) via UTF-16 surrogate pairs. */
internal fun codepointToString(inCodepoint: Int): String {
	if (inCodepoint < 0x10000) return Char(inCodepoint).toString()
	val vAdj = inCodepoint - 0x10000
	val vHigh = (0xD800 or (vAdj shr 10)).toChar()
	val vLow = (0xDC00 or (vAdj and 0x3FF)).toChar()
	return charArrayOf(vHigh, vLow).concatToString()
}
