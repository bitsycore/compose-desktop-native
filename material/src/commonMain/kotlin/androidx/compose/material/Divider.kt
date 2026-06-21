package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: Divider
// ==================

/* Thin horizontal rule. Use VerticalDivider for the vertical version. */
@Composable
fun Divider(
    modifier: Modifier = Modifier,
    color: Color = DividerDefaults.color,
    thickness: Dp = DividerDefaults.Thickness,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = DividerDefaults.color,
    thickness: Dp = DividerDefaults.Thickness,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(thickness)
            .background(color)
    )
}

object DividerDefaults {
    val Thickness: Dp = 1.dp
    val color: Color = Color(0x1FFFFFFFL)
}
