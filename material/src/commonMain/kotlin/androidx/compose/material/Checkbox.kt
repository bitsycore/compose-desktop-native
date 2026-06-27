package androidx.compose.material

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        if (checked) CheckGlyph(if (enabled) colors.checkmarkColor else colors.disabledColor)
    }
}

/* The check mark / indeterminate dash, drawn as strokes so it doesn't depend on
   a font having the ✓ / – glyphs (the bundled font lacks them). */
@Composable
private fun CheckGlyph(inColor: Color, inDash: Boolean = false) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val vW = size.width
        val vH = size.height
        val vStroke = size.minDimension * 0.14f
        if (inDash) {
            drawLine(inColor, Offset(vW * 0.26f, vH * 0.5f), Offset(vW * 0.74f, vH * 0.5f), vStroke, StrokeCap.Round)
        } else {
            drawLine(inColor, Offset(vW * 0.22f, vH * 0.52f), Offset(vW * 0.43f, vH * 0.72f), vStroke, StrokeCap.Round)
            drawLine(inColor, Offset(vW * 0.43f, vH * 0.72f), Offset(vW * 0.78f, vH * 0.30f), vStroke, StrokeCap.Round)
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
        val vColor = if (enabled) colors.checkmarkColor else colors.disabledColor
        when (state) {
            ToggleableState.On            -> CheckGlyph(vColor)
            ToggleableState.Indeterminate -> CheckGlyph(vColor, inDash = true)
            ToggleableState.Off           -> {}
        }
    }
}
