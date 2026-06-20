package androidx.compose.foundation.text

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onKeyEvent
import androidx.compose.foundation.onTextInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.currentTextMeasurer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ==================
// MARK: BasicTextField — Phase 1 MVP
// ==================

/* Compose Multiplatform exposes BasicTextField with two overloads — one
   taking a raw String (simpler, for forms that only care about the final
   text), one taking a TextFieldValue (for callers that want cursor /
   selection visibility). The String overload is just a thin wrapper that
   keeps a TextFieldValue in remember internally. */

@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    cursorColor: Color = Color.Black,
    fontSize: Sp = 16.sp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    // Mirror Compose: hold a TextFieldValue internally; pull text changes
    // from the caller's String back into it (and reset cursor to end on
    // external change).
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier,
        color = color,
        cursorColor = cursorColor,
        fontSize = fontSize,
        enabled = enabled,
        readOnly = readOnly,
    )
}

@Composable
fun BasicTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    cursorColor: Color = Color.Black,
    fontSize: Sp = 16.sp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    var cursorBlinkVisible by remember { mutableStateOf(true) }

    // Cursor blinks at 500 ms cadence while focused. Resets to visible on
    // every cursor movement / text edit (LaunchedEffect re-keys on selection).
    LaunchedEffect(isFocused, value.selection) {
        if (!isFocused) {
            cursorBlinkVisible = false
            return@LaunchedEffect
        }
        cursorBlinkVisible = true
        while (true) {
            delay(500L)
            cursorBlinkVisible = !cursorBlinkVisible
        }
    }

    // Cursor pixel offset = width of the text up to the cursor.
    val vFontSize = fontSize.value.toInt()
    val vCursorOffsetPx = if (value.selection.start <= 0) 0
        else currentTextMeasurer.measure(
            value.text.substring(0, value.selection.start.coerceAtMost(value.text.length)),
            vFontSize
        ).width

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = (fontSize.value * 1.4f).dp)
            .focusable { isFocused = it }
            .onKeyEvent { ev ->
                if (!enabled || readOnly) return@onKeyEvent false
                if (ev.key.type != KeyEventType.Down) return@onKeyEvent false
                handleKey(ev.key.keyCode, value, onValueChange)
            }
            .onTextInput { input ->
                if (!enabled || readOnly) return@onTextInput
                onValueChange(insertAtCursor(value, input))
            }
    ) {
        BasicText(text = value.text, color = color, fontSize = fontSize)

        if (isFocused && cursorBlinkVisible) {
            // Fixed height tied to fontSize. fillMaxHeight here would pick up
            // the unbounded maxHeight from the surrounding Column and pulse
            // the whole field's height every blink.
            Box(
                modifier = Modifier
                    .offset(x = vCursorOffsetPx.dp)
                    .width(1.dp)
                    .height((fontSize.value * 1.2f).dp)
                    .background(cursorColor)
            )
        }
    }
}

// ==================
// MARK: Edit helpers
// ==================

/* Insert `inText` at the current cursor / selection. Cursor moves to the
   end of the inserted text. Selection is collapsed before insertion (the
   selected range is replaced — same as Compose). */
private fun insertAtCursor(inValue: TextFieldValue, inText: String): TextFieldValue {
    val vMin = inValue.selection.min.coerceIn(0, inValue.text.length)
    val vMax = inValue.selection.max.coerceIn(0, inValue.text.length)
    val vNewText = inValue.text.substring(0, vMin) + inText + inValue.text.substring(vMax)
    val vNewCursor = vMin + inText.length
    return TextFieldValue(vNewText, TextRange(vNewCursor))
}

/* SDL scancodes we recognise for Phase 1. Treating them as raw Ints keeps
   the common module from depending on the sdl3 cinterop. */
private const val SCANCODE_BACKSPACE = 42
private const val SCANCODE_DELETE    = 76

private fun handleKey(
    inScancode: Int,
    inValue: TextFieldValue,
    inOnChange: (TextFieldValue) -> Unit,
): Boolean = when (inScancode) {
    SCANCODE_BACKSPACE -> {
        val vMin = inValue.selection.min
        val vMax = inValue.selection.max
        if (vMin == vMax && vMin == 0) {
            true
        } else {
            val vDeleteFrom = if (vMin == vMax) vMin - 1 else vMin
            val vNewText = inValue.text.substring(0, vDeleteFrom) +
                           inValue.text.substring(vMax)
            inOnChange(TextFieldValue(vNewText, TextRange(vDeleteFrom)))
            true
        }
    }
    SCANCODE_DELETE -> {
        val vMin = inValue.selection.min
        val vMax = inValue.selection.max
        if (vMin == vMax && vMax >= inValue.text.length) {
            true
        } else {
            val vDeleteTo = if (vMin == vMax) vMin + 1 else vMax
            val vNewText = inValue.text.substring(0, vMin) +
                           inValue.text.substring(vDeleteTo)
            inOnChange(TextFieldValue(vNewText, TextRange(vMin)))
            true
        }
    }
    else -> false
}
