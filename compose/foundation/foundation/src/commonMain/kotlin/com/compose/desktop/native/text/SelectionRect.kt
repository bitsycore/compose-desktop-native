package com.compose.desktop.native.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset

// ==================
// MARK: SelectionRect
// ==================

/* One selection-highlight rectangle (in layout pixels), painted behind the glyphs.
   Internal render glue shared by BasicText (selection-aware) and BasicTextField.
   Sizes come from Paragraph.getBoundingBox / getPathForRange — already pixels
   (see § HiDPI in CLAUDE.md), so use the pixel-based offset + a raw Layout
   sizing shim instead of `.dp` (which would double-scale on Retina). */
@Composable
internal fun SelectionRect(inX: Float, inY: Float, inW: Float, inH: Float, inColor: Color) {
	Box(
		modifier = Modifier
			.offset { IntOffset(inX.toInt(), inY.toInt()) }
			.layout { measurable, _ ->
				val vW = inW.toInt().coerceAtLeast(1)
				val vH = inH.toInt().coerceAtLeast(1)
				val vPlaceable = measurable.measure(
					androidx.compose.ui.unit.Constraints.fixed(vW, vH)
				)
				layout(vW, vH) { vPlaceable.place(0, 0) }
			}
			.background(inColor)
	)
}
