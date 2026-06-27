package androidx.compose.foundation.text.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ==================
// MARK: SelectionRect
// ==================

/* One selection-highlight rectangle (logical px), painted behind the glyphs.
   Internal render glue shared by BasicText (selection-aware) and BasicTextField. */
@Composable
internal fun SelectionRect(inX: Float, inY: Float, inW: Float, inH: Float, inColor: Color) {
	Box(
		modifier = Modifier
			.offset(x = inX.dp, y = inY.dp)
			.width(inW.coerceAtLeast(1f).dp)
			.height(inH.coerceAtLeast(1f).dp)
			.background(inColor)
	)
}
