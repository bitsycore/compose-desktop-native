package androidx.compose.material

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: ToggleButton
// ==================

/* A button that toggles a boolean. Looks like a flat pill that fills with
   primary when checked. Useful for toolbar toggles ("Bold", "Italic"), or
   any single-button binary state where Checkbox/Switch would be too small.
   When you need mutually-exclusive options, use SegmentedButton instead. */
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ToggleButtonDefaults.Shape,
    content: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }

    val vBg = when {
        !enabled -> Color.Transparent
        checked  -> MaterialTheme.colors.primary.copy(alpha = if (vHover) 0.32f else 0.20f)
        vHover   -> Color(0x14FFFFFFL)
        else     -> Color.Transparent
    }
    val vBorder = BorderStroke(
        width = 1.dp,
        color = if (checked) MaterialTheme.colors.primary else Color(0x33FFFFFFL),
    )

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = ToggleButtonDefaults.MinWidth,
                minHeight = ToggleButtonDefaults.MinHeight,
            )
            .background(vBg, shape)
            .border(vBorder, shape)
            .hoverable { vHover = it }
            .clickable { if (enabled) onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

object ToggleButtonDefaults {
    val MinWidth: Dp = 48.dp
    val MinHeight: Dp = 32.dp
    val Shape = RoundedCornerShape(4.dp)
}
