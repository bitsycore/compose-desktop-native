package androidx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: TextFieldColors
// ==================

data class TextFieldColors(
    val textColor: Color,
    val disabledTextColor: Color,
    val cursorColor: Color,
    val errorCursorColor: Color,
    val selectionColor: Color,
    val containerColor: Color,
    val borderColor: Color,
    val focusedBorderColor: Color,
    val errorBorderColor: Color,
    val labelColor: Color,
    val focusedLabelColor: Color,
    val errorLabelColor: Color,
    val placeholderColor: Color,
    val supportingTextColor: Color,
    val errorSupportingTextColor: Color,
)

// ==================
// MARK: TextFieldDefaults
// ==================

object TextFieldDefaults {
    val MinHeight: Dp = 56.dp
    val MinWidth: Dp = 200.dp
    val FontSize: Sp = 16.sp
    val LabelFontSize: Sp = 12.sp
    val SupportingFontSize: Sp = 12.sp

    val FilledShape: Shape = RoundedCornerShape(4.dp)
    val OutlinedShape: Shape = RoundedCornerShape(4.dp)

    val ContentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)

    // Outlined fields use uniform padding on all four sides (like Material's
    // OutlinedTextField): combined with the 56 dp min height and a centred
    // container, a single line sits vertically centred, and when the text
    // wraps to multiple lines the box grows with equal insets all around.
    val OutlinedContentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)

    @Composable
    fun textFieldColors(
        textColor: Color = MaterialTheme.colors.onSurface,
        disabledTextColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
        cursorColor: Color = MaterialTheme.colors.primary,
        errorCursorColor: Color = MaterialTheme.colors.error,
        selectionColor: Color = MaterialTheme.colors.primary.copy(alpha = 0.30f),
        containerColor: Color = MaterialTheme.colors.surface.blend(MaterialTheme.colors.onSurface, 0.04f),
        borderColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.42f),
        focusedBorderColor: Color = MaterialTheme.colors.primary,
        errorBorderColor: Color = MaterialTheme.colors.error,
        labelColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.60f),
        focusedLabelColor: Color = MaterialTheme.colors.primary,
        errorLabelColor: Color = MaterialTheme.colors.error,
        placeholderColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.42f),
        supportingTextColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.60f),
        errorSupportingTextColor: Color = MaterialTheme.colors.error,
    ) = TextFieldColors(
        textColor, disabledTextColor, cursorColor, errorCursorColor, selectionColor,
        containerColor, borderColor, focusedBorderColor, errorBorderColor,
        labelColor, focusedLabelColor, errorLabelColor,
        placeholderColor, supportingTextColor, errorSupportingTextColor,
    )

    @Composable
    fun outlinedTextFieldColors(
        containerColor: Color = Color.Transparent,
    ): TextFieldColors {
        val vBase = textFieldColors()
        return vBase.copy(containerColor = containerColor)
    }
}

// ==================
// MARK: TextField (filled, Material 1 / 3 style)
// ==================

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    fontSize: Sp = TextFieldDefaults.FontSize,
    shape: Shape = TextFieldDefaults.FilledShape,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    TextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier,
        enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, fontSize = fontSize, shape = shape, colors = colors,
    )
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    fontSize: Sp = TextFieldDefaults.FontSize,
    shape: Shape = TextFieldDefaults.FilledShape,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
) {
    TextFieldImpl(
        value = value, onValueChange = onValueChange,
        outerModifier = modifier,
        enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, fontSize = fontSize, shape = shape, colors = colors,
        outlined = false,
    )
}

// ==================
// MARK: OutlinedTextField
// ==================

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    fontSize: Sp = TextFieldDefaults.FontSize,
    shape: Shape = TextFieldDefaults.OutlinedShape,
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(),
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier,
        enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, fontSize = fontSize, shape = shape, colors = colors,
    )
}

@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    fontSize: Sp = TextFieldDefaults.FontSize,
    shape: Shape = TextFieldDefaults.OutlinedShape,
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(),
) {
    TextFieldImpl(
        value = value, onValueChange = onValueChange,
        outerModifier = modifier,
        enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, fontSize = fontSize, shape = shape, colors = colors,
        outlined = true,
    )
}

// ==================
// MARK: Shared implementation
// ==================

@Composable
private fun TextFieldImpl(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    outerModifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    singleLine: Boolean,
    label: String?,
    placeholder: String?,
    supportingText: String?,
    isError: Boolean,
    fontSize: Sp,
    shape: Shape,
    colors: TextFieldColors,
    outlined: Boolean,
) {
    var isFocused by remember { mutableStateOf(false) }

    val vBorderColor = when {
        isError    -> colors.errorBorderColor
        isFocused  -> colors.focusedBorderColor
        else       -> colors.borderColor
    }
    val vLabelColor = when {
        isError   -> colors.errorLabelColor
        isFocused -> colors.focusedLabelColor
        else      -> colors.labelColor
    }
    val vTextColor = if (enabled) colors.textColor else colors.disabledTextColor
    val vCursorColor = if (isError) colors.errorCursorColor else colors.cursorColor
    val vBorderWidth: Dp = if (isFocused || isError) 2.dp else 1.dp

    Column(modifier = outerModifier) {
        // The text field's main container. Uses defaultMinSize for the height
        // so multi-line / soft-wrapped content can grow the box; .height would
        // pin it at 56 dp and clip wrapped lines.
        var containerMod: Modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TextFieldDefaults.MinHeight)
        containerMod = if (outlined) {
            containerMod
                .background(colors.containerColor, shape)
                .border(vBorderWidth, vBorderColor, shape)
        } else {
            // Filled style: tinted container + bottom indicator line. The
            // "bottom indicator" is implemented as a 1/2-dp border with a
            // square (non-rounded) shape so only the bottom shows. For our
            // minimal version we just use border() with the requested shape
            // and rely on the rounded top edges to differentiate.
            containerMod
                .background(colors.containerColor, shape)
                .border(vBorderWidth, vBorderColor, shape)
        }

        // Centre the content so a single line sits vertically centred in the
        // 56 dp min height; with a multi-line value the box grows and the
        // padding reads as equal insets top/bottom. Outlined uses uniform
        // 16 dp padding (like Material's OutlinedTextField); filled is a touch
        // more compact (12/8) but centred the same way.
        val vPad = if (outlined) TextFieldDefaults.OutlinedContentPadding else TextFieldDefaults.ContentPadding
        Box(
            modifier = containerMod,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = vPad.start,
                        end   = vPad.end,
                        top   = vPad.top,
                        bottom = vPad.bottom,
                    ),
            ) {
                if (label != null) {
                    BasicText(
                        text = label,
                        color = vLabelColor,
                        fontSize = TextFieldDefaults.LabelFontSize,
                    )
                }

                // Placeholder shows when empty AND not focused.
                Box {
                    if (value.text.isEmpty() && !isFocused && placeholder != null) {
                        BasicText(
                            text = placeholder,
                            color = colors.placeholderColor,
                            fontSize = fontSize,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        color = vTextColor,
                        cursorColor = vCursorColor,
                        selectionColor = colors.selectionColor,
                        fontSize = fontSize,
                        enabled = enabled,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        onFocusChanged = { isFocused = it },
                    )
                }
            }
        }

        if (supportingText != null) {
            BasicText(
                text = supportingText,
                color = if (isError) colors.errorSupportingTextColor else colors.supportingTextColor,
                fontSize = TextFieldDefaults.SupportingFontSize,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}
