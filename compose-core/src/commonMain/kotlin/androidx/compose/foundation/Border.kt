package androidx.compose.foundation

import androidx.compose.ui.BorderModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

// ==================
// MARK: BorderStroke
// ==================

data class BorderStroke(val width: Dp, val color: Color)

// ==================
// MARK: Modifier.border()
// ==================

fun Modifier.border(width: Dp, color: Color, shape: Shape = RectangleShape) =
    then(BorderModifier(width.value.toInt(), color, shape))

fun Modifier.border(border: BorderStroke, shape: Shape = RectangleShape) =
    then(BorderModifier(border.width.value.toInt(), border.color, shape))
