package androidx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

// ==================
// MARK: Surface
// ==================

/* Material's container primitive — applies a shape-aware background, an
   optional border, and clips its children to that same shape. Most
   higher-level components (Card, TextField outlines, dialog backgrounds)
   build on top of it. */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colors.surface,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    var m: Modifier = modifier.background(color, shape).clip(shape)
    if (border != null) m = m.border(border, shape)
    Box(modifier = m, content = content)
}
