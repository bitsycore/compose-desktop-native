package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: Checkbox
// ==================

/* Material 1 checkbox. Filled square + "✓" when checked, outlined empty
   square when not. The check glyph is rendered as a Unicode character
   ("✓") so we don't need a vector primitive. */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) {
    val vBox: Color = when {
        !enabled -> colors.disabledColor
        checked  -> colors.checkedColor
        else     -> Color.Transparent
    }
    val vBorderColor: Color = when {
        !enabled -> colors.disabledColor
        checked  -> colors.checkedColor
        else     -> colors.uncheckedColor
    }

    var m: Modifier = modifier
        .size(CheckboxDefaults.Size)
        .background(vBox, CheckboxDefaults.Shape)
        .border(2.dp, vBorderColor, CheckboxDefaults.Shape)
    if (onCheckedChange != null) {
        m = m.clickable { if (enabled) onCheckedChange(!checked) }
    }

    Box(modifier = m, contentAlignment = Alignment.Center) {
        if (checked) {
            Text(
                text = "✓",
                color = if (enabled) colors.checkmarkColor else colors.disabledColor,
                fontSize = 14.sp,
            )
        }
    }
}

// ==================
// MARK: CheckboxColors / Defaults
// ==================

data class CheckboxColors(
    val checkedColor: Color,
    val uncheckedColor: Color,
    val checkmarkColor: Color,
    val disabledColor: Color,
)

object CheckboxDefaults {
    val Size: Dp = 20.dp
    val Shape = RoundedCornerShape(2.dp)

    @Composable
    fun colors(
        checkedColor: Color = MaterialTheme.colors.secondary,
        uncheckedColor: Color = Color(0x99FFFFFFL),
        checkmarkColor: Color = MaterialTheme.colors.onSecondary,
        disabledColor: Color = Color(0x66FFFFFFL),
    ) = CheckboxColors(checkedColor, uncheckedColor, checkmarkColor, disabledColor)
}

// ==================
// MARK: TriStateCheckbox
// ==================

enum class ToggleableState { On, Off, Indeterminate }

/* Three-state variant with an em-dash for the Indeterminate state. Useful for
   "select all" parent checkboxes whose children are partially selected. */
@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) {
    val vFilled = state != ToggleableState.Off
    val vBox: Color = when {
        !enabled -> colors.disabledColor
        vFilled  -> colors.checkedColor
        else     -> Color.Transparent
    }
    val vBorderColor: Color = when {
        !enabled -> colors.disabledColor
        vFilled  -> colors.checkedColor
        else     -> colors.uncheckedColor
    }

    var m: Modifier = modifier
        .size(CheckboxDefaults.Size)
        .background(vBox, CheckboxDefaults.Shape)
        .border(2.dp, vBorderColor, CheckboxDefaults.Shape)
    if (onClick != null) {
        m = m.clickable { if (enabled) onClick() }
    }

    Box(modifier = m, contentAlignment = Alignment.Center) {
        val vGlyph = when (state) {
            ToggleableState.On            -> "✓"
            ToggleableState.Indeterminate -> "–"
            ToggleableState.Off           -> ""
        }
        if (vGlyph.isNotEmpty()) {
            Text(
                text = vGlyph,
                color = if (enabled) colors.checkmarkColor else colors.disabledColor,
                fontSize = 14.sp,
            )
        }
    }
}
