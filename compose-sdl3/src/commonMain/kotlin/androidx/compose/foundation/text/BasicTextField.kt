package androidx.compose.foundation.text

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onDrag
import androidx.compose.foundation.onKeyEvent
import androidx.compose.foundation.onTextInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.currentTextMeasurer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

// Monotonic-clock millisecond source for double-click detection. Anchored at
// the first read; subsequent reads return elapsed ms since that anchor.
private val kClockStart = TimeSource.Monotonic.markNow()
private fun nowMillis(): Long = kClockStart.elapsedNow().inWholeMilliseconds

// ==================
// MARK: BasicTextField — Phase 2 (editing mid-text)
// ==================

@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    cursorColor: Color = Color.Black,
    selectionColor: Color = Color(0x661E88E5L),
    fontSize: Sp = 16.sp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
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
        selectionColor = selectionColor,
        fontSize = fontSize,
        enabled = enabled,
        readOnly = readOnly,
        onFocusChanged = onFocusChanged,
    )
}

@Composable
fun BasicTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    cursorColor: Color = Color.Black,
    selectionColor: Color = Color(0x661E88E5L),
    fontSize: Sp = 16.sp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    var cursorBlinkVisible by remember { mutableStateOf(true) }
    var dragAnchor by remember { mutableStateOf(-1) }
    // Double-click detection: timestamp + char index of the previous press
    var lastPressMs by remember { mutableStateOf(0L) }
    var lastPressIndex by remember { mutableStateOf(-1) }
    // Undo / redo stacks of TextFieldValue snapshots. Most-recent at top.
    // We push a snapshot before each "edit run" (a sequence of typings is
    // grouped, but any non-typing edit closes the run).
    val undoStack = remember { mutableListOf<TextFieldValue>() }
    val redoStack = remember { mutableListOf<TextFieldValue>() }
    // Are the previous and next edits both typing? When true, we don't
    // push a new undo snapshot (so a word's worth of typing is one undo).
    var inTypingRun by remember { mutableStateOf(false) }

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

    val vFontSize = fontSize.value.toInt()
    val vCursorOffsetPx = prefixWidth(value.text, value.selection.end.coerceIn(0, value.text.length), vFontSize)

    // Edit helpers: push undo snapshots, manage the typing-run flag, fire the
    // caller's onValueChange. typingEdit collapses consecutive typings into a
    // single undo step; structuralEdit (backspace / delete / paste / cut) ends
    // the typing run and snapshots.
    val pushSnapshot: () -> Unit = {
        undoStack.add(value)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
    }
    val typingEdit: (TextFieldValue) -> Unit = { nv ->
        if (!inTypingRun) pushSnapshot()
        inTypingRun = true
        onValueChange(nv)
    }
    val structuralEdit: (TextFieldValue) -> Unit = { nv ->
        pushSnapshot()
        inTypingRun = false
        onValueChange(nv)
    }
    val cursorOnlyEdit: (TextFieldValue) -> Unit = { nv ->
        inTypingRun = false
        onValueChange(nv)
    }
    val doUndo: () -> Unit = {
        val prev = undoStack.removeLastOrNull()
        if (prev != null) {
            redoStack.add(value)
            inTypingRun = false
            onValueChange(prev)
        }
    }
    val doRedo: () -> Unit = {
        val next = redoStack.removeLastOrNull()
        if (next != null) {
            undoStack.add(value)
            inTypingRun = false
            onValueChange(next)
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = (fontSize.value * 1.4f).dp)
            .focusable {
                isFocused = it
                onFocusChanged(it)
            }
            .onDrag(
                onStart = { relX, _ ->
                    if (!enabled) return@onDrag
                    val vIndex = charIndexAtX(value.text, vFontSize, relX)
                    val vNow = nowMillis()
                    val vIsDoubleClick = vIndex == lastPressIndex && (vNow - lastPressMs) < 350
                    lastPressMs = vNow
                    lastPressIndex = vIndex
                    if (vIsDoubleClick) {
                        val vWordStart = wordBoundaryLeft(value.text, vIndex + 1)
                        val vWordEnd = wordBoundaryRight(value.text, vIndex)
                        dragAnchor = vWordStart
                        cursorOnlyEdit(value.copy(selection = TextRange(vWordStart, vWordEnd)))
                    } else {
                        dragAnchor = vIndex
                        cursorOnlyEdit(value.copy(selection = TextRange(vIndex)))
                    }
                },
                onDrag = { relX, _ ->
                    if (!enabled || dragAnchor < 0) return@onDrag
                    val vIndex = charIndexAtX(value.text, vFontSize, relX)
                    cursorOnlyEdit(value.copy(selection = TextRange(dragAnchor, vIndex)))
                },
                onEnd = {
                    dragAnchor = -1
                },
            )
            .onKeyEvent { ev ->
                if (!enabled) return@onKeyEvent false
                if (ev.key.type != KeyEventType.Down) return@onKeyEvent false
                handleKey(ev.key, value, readOnly, structuralEdit, cursorOnlyEdit, doUndo, doRedo)
            }
            .onTextInput { input ->
                if (!enabled || readOnly) return@onTextInput
                typingEdit(insertAtCursor(value, input))
            }
    ) {
        // Selection rect drawn FIRST so it sits behind glyphs. Drawn only
        // when selection is non-collapsed.
        if (!value.selection.collapsed) {
            val vSelStartPx = prefixWidth(value.text, value.selection.min, vFontSize)
            val vSelEndPx = prefixWidth(value.text, value.selection.max, vFontSize)
            val vSelWidth = (vSelEndPx - vSelStartPx).coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .offset(x = vSelStartPx.dp)
                    .width(vSelWidth.dp)
                    .height((fontSize.value * 1.2f).dp)
                    .background(selectionColor)
            )
        }

        BasicText(text = value.text, color = color, fontSize = fontSize)

        if (isFocused && cursorBlinkVisible) {
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

private fun insertAtCursor(inValue: TextFieldValue, inText: String): TextFieldValue {
    val vMin = inValue.selection.min.coerceIn(0, inValue.text.length)
    val vMax = inValue.selection.max.coerceIn(0, inValue.text.length)
    val vNewText = inValue.text.substring(0, vMin) + inText + inValue.text.substring(vMax)
    val vNewCursor = vMin + inText.length
    return TextFieldValue(vNewText, TextRange(vNewCursor))
}

/* Width of `text[0..end]` in pixels using the active TextMeasurer. */
private fun prefixWidth(inText: String, inEnd: Int, inFontSize: Int): Int {
    if (inEnd <= 0 || inText.isEmpty()) return 0
    val vEnd = inEnd.coerceAtMost(inText.length)
    return currentTextMeasurer.measure(inText.substring(0, vEnd), inFontSize).width
}

/* Character index whose left edge is closest to `inX` pixels from the text
   start. Linear scan growing the prefix one char at a time — heuristic
   measurer is cheap so O(n) per click is fine. */
private fun charIndexAtX(inText: String, inFontSize: Int, inX: Int): Int {
    if (inX <= 0 || inText.isEmpty()) return 0
    var vPrev = 0
    for (i in inText.indices) {
        val vNext = currentTextMeasurer.measure(inText.substring(0, i + 1), inFontSize).width
        if (vNext > inX) {
            return if ((inX - vPrev) < (vNext - inX)) i else i + 1
        }
        vPrev = vNext
    }
    return inText.length
}

/* Word-boundary helpers — Compose's "word" = whitespace-delimited run. */
private fun wordBoundaryLeft(inText: String, inFrom: Int): Int {
    var i = inFrom.coerceAtMost(inText.length)
    while (i > 0 && inText[i - 1].isWhitespace()) i--
    while (i > 0 && !inText[i - 1].isWhitespace()) i--
    return i
}

private fun wordBoundaryRight(inText: String, inFrom: Int): Int {
    var i = inFrom.coerceAtLeast(0)
    while (i < inText.length && inText[i].isWhitespace()) i++
    while (i < inText.length && !inText[i].isWhitespace()) i++
    return i
}

// ==================
// MARK: Scancodes
// ==================
// SDL3 keyboard scancodes — values match SDL's enum. Kept here so the common
// module doesn't depend on the sdl3 cinterop. These are scancode values
// (physical key positions), not keysyms.

private const val SCANCODE_A          = 4
private const val SCANCODE_C          = 6
private const val SCANCODE_V          = 25
private const val SCANCODE_X          = 27
private const val SCANCODE_Z          = 29
private const val SCANCODE_BACKSPACE  = 42
private const val SCANCODE_RIGHT      = 79
private const val SCANCODE_LEFT       = 80
private const val SCANCODE_DELETE     = 76
private const val SCANCODE_HOME       = 74
private const val SCANCODE_END        = 77

/* Selection update rule shared by every navigation key: build the new
   cursor head; if Shift is held, keep selection.start (the anchor) and
   move only end; otherwise collapse to the new head. */
private fun moveCursor(
    inValue: TextFieldValue,
    inNewHead: Int,
    inExtendSelection: Boolean,
): TextFieldValue {
    val vHead = inNewHead.coerceIn(0, inValue.text.length)
    val vSelection = if (inExtendSelection) {
        TextRange(inValue.selection.start, vHead)
    } else {
        TextRange(vHead)
    }
    return inValue.copy(selection = vSelection)
}

private fun handleKey(
    inKey: KeyEvent,
    inValue: TextFieldValue,
    inReadOnly: Boolean,
    inStructuralEdit: (TextFieldValue) -> Unit,
    inCursorOnlyEdit: (TextFieldValue) -> Unit,
    inUndo: () -> Unit,
    inRedo: () -> Unit,
): Boolean {
    val vMods = inKey.modifiers
    val vShift = vMods.shift
    val vMeta = vMods.meta   // Cmd on macOS
    val vAlt = vMods.alt     // Option on macOS, Alt elsewhere

    when (inKey.keyCode) {
        SCANCODE_LEFT -> {
            val vCurrent = inValue.selection.end
            val vNewHead = when {
                vMeta -> 0
                vAlt  -> wordBoundaryLeft(inValue.text, vCurrent)
                !vShift && !inValue.selection.collapsed -> inValue.selection.min
                else  -> vCurrent - 1
            }
            inCursorOnlyEdit(moveCursor(inValue, vNewHead, vShift))
            return true
        }
        SCANCODE_RIGHT -> {
            val vCurrent = inValue.selection.end
            val vNewHead = when {
                vMeta -> inValue.text.length
                vAlt  -> wordBoundaryRight(inValue.text, vCurrent)
                !vShift && !inValue.selection.collapsed -> inValue.selection.max
                else  -> vCurrent + 1
            }
            inCursorOnlyEdit(moveCursor(inValue, vNewHead, vShift))
            return true
        }
        SCANCODE_HOME -> {
            inCursorOnlyEdit(moveCursor(inValue, 0, vShift))
            return true
        }
        SCANCODE_END -> {
            inCursorOnlyEdit(moveCursor(inValue, inValue.text.length, vShift))
            return true
        }
        SCANCODE_A -> if (vMeta) {
            inCursorOnlyEdit(inValue.copy(selection = TextRange(0, inValue.text.length)))
            return true
        }
        SCANCODE_C -> if (vMeta && !inValue.selection.collapsed) {
            // Copy selection — non-destructive, doesn't go through the edit
            // helpers. Bare clipboard write.
            val vSel = inValue.text.substring(inValue.selection.min, inValue.selection.max)
            currentClipboard.setText(vSel)
            return true
        }
        SCANCODE_X -> if (vMeta && !inValue.selection.collapsed && !inReadOnly) {
            val vSel = inValue.text.substring(inValue.selection.min, inValue.selection.max)
            currentClipboard.setText(vSel)
            val vNewText = inValue.text.substring(0, inValue.selection.min) +
                           inValue.text.substring(inValue.selection.max)
            inStructuralEdit(TextFieldValue(vNewText, TextRange(inValue.selection.min)))
            return true
        }
        SCANCODE_V -> if (vMeta && !inReadOnly) {
            val vPaste = currentClipboard.getText() ?: return true
            inStructuralEdit(insertAtCursor(inValue, vPaste))
            return true
        }
        SCANCODE_Z -> if (vMeta) {
            if (vShift) inRedo() else inUndo()
            return true
        }
        SCANCODE_BACKSPACE -> if (!inReadOnly) {
            val vMin = inValue.selection.min
            val vMax = inValue.selection.max
            if (vMin == vMax && vMin == 0) return true
            val vDeleteFrom = if (vMin == vMax) vMin - 1 else vMin
            val vNewText = inValue.text.substring(0, vDeleteFrom) +
                           inValue.text.substring(vMax)
            inStructuralEdit(TextFieldValue(vNewText, TextRange(vDeleteFrom)))
            return true
        }
        SCANCODE_DELETE -> if (!inReadOnly) {
            val vMin = inValue.selection.min
            val vMax = inValue.selection.max
            if (vMin == vMax && vMax >= inValue.text.length) return true
            val vDeleteTo = if (vMin == vMax) vMin + 1 else vMax
            val vNewText = inValue.text.substring(0, vMin) +
                           inValue.text.substring(vDeleteTo)
            inStructuralEdit(TextFieldValue(vNewText, TextRange(vMin)))
            return true
        }
    }
    return false
}
