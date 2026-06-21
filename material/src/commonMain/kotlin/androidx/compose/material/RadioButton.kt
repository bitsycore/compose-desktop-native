package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: RadioButton
// ==================

/* Outer ring + inner filled dot when selected. Same modifier-stack approach
   as Checkbox, but the shapes are CircleShape, and the selected fill is the
   inner dot (not the outer body). */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) {
    val vRing: Color = when {
        !enabled -> colors.disabledColor
        selected -> colors.selectedColor
        else     -> colors.unselectedColor
    }

    var m: Modifier = modifier
        .size(RadioButtonDefaults.Size)
        .border(2.dp, vRing, CircleShape)
    if (onClick != null) {
        m = m.clickable { if (enabled) onClick() }
    }

    Box(modifier = m, contentAlignment = Alignment.Center) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(RadioButtonDefaults.DotSize)
                    .background(if (enabled) colors.selectedColor else colors.disabledColor, CircleShape)
            )
        }
    }
}

// ==================
// MARK: RadioButtonColors / Defaults
// ==================

data class RadioButtonColors(
    val selectedColor: Color,
    val unselectedColor: Color,
    val disabledColor: Color,
)

object RadioButtonDefaults {
    val Size: Dp = 20.dp
    val DotSize: Dp = 10.dp

    @Composable
    fun colors(
        selectedColor: Color = MaterialTheme.colors.secondary,
        unselectedColor: Color = Color(0x99FFFFFFL),
        disabledColor: Color = Color(0x66FFFFFFL),
    ) = RadioButtonColors(selectedColor, unselectedColor, disabledColor)
}
