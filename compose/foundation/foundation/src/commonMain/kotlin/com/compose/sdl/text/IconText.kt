package com.compose.sdl.text

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit

// ==================
// MARK: IconText — project-only text composable for icon fonts
// ==================

/**
 A minimal project text composable that renders a codepoint through a named
 icon font with optional variable-font axis settings (Material Symbols).

 Sits outside `androidx.compose.foundation.text.BasicText` so BasicText can
 remain a byte-identical match of upstream. Icons need:
   - `fontFamily: String` — the registered icon font family name (project's
     `FontFamily.Named`, not upstream `FontFamily.Resolver`).
   - `fontVariationSettings` — per-usage variable-font axis values (FILL,
     wght, GRAD, opsz for Material Symbols).

 Upstream `TextStyle` has neither: fontFamily routes through the
 `FontFamily.Resolver`, and axis values are set per-Font at construction
 time. Rather than fight that abstraction for one composable, icons use
 this project path — the TextDrawElement modifier feeds directly into
 SdlParagraph / Sdl3Canvas.drawNativeText.

 Material `Icon(codepoint = ..., fontFamily = ...)` uses this. Everything
 else (Text, BasicText, TextField) goes through the upstream-shaped path.
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
	// Layout runs in physical pixels (LocalDensity = DPR), so the icon font
	// size must also convert `sp → px`. Matches SdlParagraph's `fontSize.value
	// * density`. Without this the icons render at half size on Retina.
	val vDensity = LocalDensity.current.density
	val vFontPx = (fontSize.value * vDensity).toInt().coerceAtLeast(1)
	Box(modifier = modifier) {
		androidx.compose.ui.layout.Layout(
			modifier = TextDrawElement(
				text = text,
				spans = null,
				color = if (color == Color.Unspecified) Color.Black else color,
				fontSizePx = vFontPx,
				textAlign = textAlign,
				softWrap = false,
				fontFamily = fontFamily,
				fontVariations = fontVariationSettings,
			),
		) { _, constraints ->
			val vSize = currentTextMeasurer.measure(text, vFontPx, Int.MAX_VALUE, fontFamily, fontVariationSettings)
			val w = vSize.width.coerceIn(constraints.minWidth, constraints.maxWidth)
			val h = vSize.height.coerceIn(constraints.minHeight, constraints.maxHeight)
			layout(w, h) {}
		}
	}
}
