package androidx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pressable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: Button (filled)
// ==================

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    val bgColor = when {
        !enabled  -> colors.disabledBackgroundColor
        isPressed -> colors.pressedBackgroundColor
        isHovered -> colors.hoveredBackgroundColor
        else      -> colors.backgroundColor
    }

    var m: Modifier = modifier
        .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
        .background(bgColor, shape)
        .clip(shape)
    if (border != null) m = m.border(border, shape)
    m = m
        .hoverable { isHovered = it }
        .pressable { isPressed = it }
        .padding(
            start = contentPadding.start,
            top = contentPadding.top,
            end = contentPadding.end,
            bottom = contentPadding.bottom
        )
        .clickable { if (enabled) onClick() }

    Box(modifier = m, contentAlignment = Alignment.Center, content = content)
}

// ==================
// MARK: OutlinedButton
// ==================

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    border: BorderStroke = ButtonDefaults.outlinedBorder,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = border,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}

// ==================
// MARK: TextButton
// ==================

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = null,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}

// ==================
// MARK: PaddingValues
// ==================

data class PaddingValues(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp
) {
    constructor(all: Dp) : this(all, all, all, all)
    constructor(horizontal: Dp = 0.dp, vertical: Dp = 0.dp)
        : this(horizontal, vertical, horizontal, vertical)
}

// ==================
// MARK: ButtonColors
// ==================

data class ButtonColors(
    val backgroundColor: Color,
    val contentColor: Color,
    val hoveredBackgroundColor: Color,
    val pressedBackgroundColor: Color,
    val disabledBackgroundColor: Color = Color(0x1FFFFFFFL),
    val disabledContentColor: Color = Color(0x66FFFFFFL)
)

// ==================
// MARK: ButtonDefaults
// ==================

object ButtonDefaults {
    val MinWidth: Dp = 64.dp
    val MinHeight: Dp = 36.dp

    val shape: Shape = RoundedCornerShape(4.dp)

    val ContentPadding: PaddingValues = PaddingValues(
        start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp
    )

    val TextButtonContentPadding: PaddingValues = PaddingValues(
        start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp
    )

    val OutlinedBorderSize: Dp = 1.dp

    val outlinedBorder: BorderStroke
        get() = BorderStroke(OutlinedBorderSize, Color(0x1FFFFFFFL))

    /* Material state-layer alphas (Material 1 close approximation):
       hover ≈ 8%, pressed ≈ 12%. We mix the background toward the content
       color to produce the overlay. For transparent-bg variants we instead
       mix Transparent toward the content color so the overlay appears
       against whatever sits behind the button. */
    private const val HOVER_ALPHA = 0.08f
    private const val PRESS_ALPHA = 0.12f

    @Composable
    fun buttonColors(
        backgroundColor: Color = MaterialTheme.colors.primary,
        contentColor: Color = MaterialTheme.colors.onPrimary,
        hoveredBackgroundColor: Color = backgroundColor.blend(contentColor, HOVER_ALPHA),
        pressedBackgroundColor: Color = backgroundColor.blend(contentColor, PRESS_ALPHA),
        disabledBackgroundColor: Color = Color(0x1FFFFFFFL),
        disabledContentColor: Color = Color(0x66FFFFFFL)
    ) = ButtonColors(
        backgroundColor, contentColor,
        hoveredBackgroundColor, pressedBackgroundColor,
        disabledBackgroundColor, disabledContentColor
    )

    @Composable
    fun outlinedButtonColors(
        backgroundColor: Color = Color.Transparent,
        contentColor: Color = MaterialTheme.colors.primary,
        hoveredBackgroundColor: Color = contentColor.copy(alpha = HOVER_ALPHA),
        pressedBackgroundColor: Color = contentColor.copy(alpha = PRESS_ALPHA),
        disabledBackgroundColor: Color = Color.Transparent,
        disabledContentColor: Color = Color(0x66FFFFFFL)
    ) = ButtonColors(
        backgroundColor, contentColor,
        hoveredBackgroundColor, pressedBackgroundColor,
        disabledBackgroundColor, disabledContentColor
    )

    @Composable
    fun textButtonColors(
        backgroundColor: Color = Color.Transparent,
        contentColor: Color = MaterialTheme.colors.primary,
        hoveredBackgroundColor: Color = contentColor.copy(alpha = HOVER_ALPHA),
        pressedBackgroundColor: Color = contentColor.copy(alpha = PRESS_ALPHA),
        disabledBackgroundColor: Color = Color.Transparent,
        disabledContentColor: Color = Color(0x66FFFFFFL)
    ) = ButtonColors(
        backgroundColor, contentColor,
        hoveredBackgroundColor, pressedBackgroundColor,
        disabledBackgroundColor, disabledContentColor
    )
}
