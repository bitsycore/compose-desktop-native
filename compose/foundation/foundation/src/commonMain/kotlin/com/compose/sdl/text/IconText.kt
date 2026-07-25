package com.compose.sdl.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

// ==================
// MARK: IconText — icon-font text on the standard text path
// ==================

/**
 A minimal project text composable that renders a codepoint through a named
 icon font with optional variable-font axis settings (Material Symbols FILL /
 wght / GRAD / opsz).

 Now just a [BasicText]: the icon font family and its variable axes are threaded
 through [namedFontFamily] (`axes = …`), which the skiko text engine reads via
 `FontFamily.projectFontVariations()` and applies to the typeface. So icons
 measure + draw through the same skiko `skparagraph` path as ordinary text — no
 separate renderer/measurer seam.

 Material `Icon(codepoint = …, fontFamily = …)` uses this; Text / BasicText /
 TextField go through the upstream-shaped path already.
*/
@Composable
fun IconText(
	text: String,
	fontFamily: String,
	modifier: Modifier = Modifier,
	color: Color = Color.Unspecified,
	fontSize: TextUnit,
	textAlign: TextAlign = TextAlign.Start,
	fontVariationSettings: List<FontVariation.Setting>? = null,
) {
	BasicText(
		text = text,
		modifier = modifier,
		style = TextStyle(
			color = if (color == Color.Unspecified) Color.Black else color,
			fontSize = fontSize,
			fontFamily = namedFontFamily(fontFamily, axes = fontVariationSettings),
			textAlign = textAlign,
		),
		softWrap = false,
		maxLines = 1,
	)
}
