package androidx.compose.foundation.text

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onDrag
import androidx.compose.foundation.onKeyEvent
import androidx.compose.foundation.onSizeChanged
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
import androidx.compose.ui.text.WrappedText
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
    singleLine: Boolean = false,
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
        singleLine = singleLine,
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
    singleLine: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    var cursorBlinkVisible by remember { mutableStateOf(true) }
    var dragAnchor by remember { mutableStateOf(-1) }
    var lastPressMs by remember { mutableStateOf(0L) }
    var lastPressIndex by remember { mutableStateOf(-1) }
    var preferredCol by remember { mutableStateOf(-1) }
    // Measured width of the field used for soft-wrap. Updated by onSizeChanged
    // after every measure pass; stays Int.MAX_VALUE until the first measure
    // so initial composition gets the no-wrap path.
    var fieldWidthPx by remember { mutableStateOf(Int.MAX_VALUE) }
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
    // Pull the renderer's exact line height — sub-pixel-accurate so multi-
    // line click / cursor positions line up with what's actually drawn.
    val vLineHeight = currentTextMeasurer.lineHeight(vFontSize)

    // Compute the wrapped layout once per composition. Single-line forces
    // unbounded wrap so explicit \n becomes one logical line per segment
    // (and there shouldn't be any since Return is suppressed below).
    val vWrapWidth = if (singleLine) Int.MAX_VALUE else fieldWidthPx
    val vWrap: WrappedText = currentTextMeasurer.wrap(value.text, vFontSize, vWrapWidth)

    // Cursor position in wrapped coordinates + pixel position.
    val vCursorIdx = value.selection.end.coerceIn(0, value.text.length)
    val (vCursorLine, vCursorCol) = wrappedPosOf(vWrap, vCursorIdx)
    val vCursorLineText = vWrap.lines.getOrElse(vCursorLine) { "" }
    val vCursorOffsetPx = prefixWidth(vCursorLineText, vCursorCol, vFontSize)
    val vCursorYPx = (vCursorLine * vLineHeight)

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
            .onSizeChanged { fieldWidthPx = it.width }
            .focusable {
                isFocused = it
                onFocusChanged(it)
            }
            .onDrag(
                onStart = { relX, relY ->
                    if (!enabled) return@onDrag
                    val vIndex = charIndexAtWrappedPoint(vWrap, vFontSize, relX, relY, vLineHeight)
                    val vNow = nowMillis()
                    val vIsDoubleClick = vIndex == lastPressIndex && (vNow - lastPressMs) < 350
                    lastPressMs = vNow
                    lastPressIndex = vIndex
                    preferredCol = -1
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
                onDrag = { relX, relY ->
                    if (!enabled || dragAnchor < 0) return@onDrag
                    val vIndex = charIndexAtWrappedPoint(vWrap, vFontSize, relX, relY, vLineHeight)
                    cursorOnlyEdit(value.copy(selection = TextRange(dragAnchor, vIndex)))
                },
                onEnd = {
                    dragAnchor = -1
                },
            )
            .onKeyEvent { ev ->
                if (!enabled) return@onKeyEvent false
                if (ev.key.type != KeyEventType.Down) return@onKeyEvent false
                handleKey(
                    inKey = ev.key,
                    inValue = value,
                    inWrap = vWrap,
                    inFontSize = vFontSize,
                    inReadOnly = readOnly,
                    inSingleLine = singleLine,
                    inGetPrefColX = { preferredCol },
                    inSetPrefColX = { preferredCol = it },
                    inStructuralEdit = structuralEdit,
                    inCursorOnlyEdit = cursorOnlyEdit,
                    inTypingEdit = typingEdit,
                    inUndo = doUndo,
                    inRedo = doRedo,
                )
            }
            .onTextInput { input ->
                if (!enabled || readOnly) return@onTextInput
                // Strip newlines from IME-committed text when single-line.
                val vSafe = if (singleLine) input.replace("\n", "").replace("\r", "") else input
                if (vSafe.isEmpty()) return@onTextInput
                typingEdit(insertAtCursor(value, vSafe))
            }
    ) {
        // Selection rect(s) drawn FIRST so they sit behind glyphs. Multi-line
        // selections emit one rect per spanned line — wrap-aware.
        if (!value.selection.collapsed) {
            val (vMinLine, vMinCol) = wrappedPosOf(vWrap, value.selection.min)
            val (vMaxLine, vMaxCol) = wrappedPosOf(vWrap, value.selection.max)
            for (vLine in vMinLine..vMaxLine) {
                val vLineStr = vWrap.lines.getOrElse(vLine) { "" }
                val vStartCol = if (vLine == vMinLine) vMinCol else 0
                val vEndCol = if (vLine == vMaxLine) vMaxCol else vLineStr.length
                val vSx = prefixWidth(vLineStr, vStartCol, vFontSize)
                val vEx = prefixWidth(vLineStr, vEndCol, vFontSize)
                Box(
                    modifier = Modifier
                        .offset(x = vSx.dp, y = (vLine * vLineHeight).dp)
                        .width((vEx - vSx).coerceAtLeast(1).dp)
                        .height(vLineHeight.dp)
                        .background(selectionColor)
                )
            }
        }

        // Inner BasicText: wraps when !singleLine so visible text matches the
        // wrap our cursor / selection math used. fillMaxWidth so it gets the
        // same width constraint our onSizeChanged saw.
        BasicText(
            text = value.text,
            color = color,
            fontSize = fontSize,
            softWrap = !singleLine,
            modifier = if (singleLine) Modifier else Modifier.fillMaxWidth(),
        )

        if (isFocused && cursorBlinkVisible) {
            Box(
                modifier = Modifier
                    .offset(x = vCursorOffsetPx.dp, y = vCursorYPx.dp)
                    .width(1.dp)
                    .height(vLineHeight.dp)
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
   start. Linear scan growing the prefix one char at a time. Single-line. */
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

/* Multi-line equivalent: pick the line based on relY / lineHeight, then run
   charIndexAtX inside that line. Lines past the last clamp to the bottom
   line. */
private fun charIndexAtPoint(
    inText: String,
    inFontSize: Int,
    inX: Int,
    inY: Int,
    inLineHeight: Float,
): Int {
    val vLines = lineCount(inText)
    val vLine = (inY / inLineHeight).toInt().coerceIn(0, vLines - 1)
    val vLineStr = lineText(inText, vLine)
    val vCol = charIndexAtX(vLineStr, inFontSize, inX)
    return lineStart(inText, vLine) + vCol
}

// ==================
// MARK: Wrap-aware mapping
// ==================

/* Convert a cursor index into the original text into (wrappedLine, column).
   The cursor may sit at the boundary of a hard '\n' — we keep it at the END
   of the preceding line in that case, not the start of the next one. */
private fun wrappedPosOf(inWrap: WrappedText, inCursor: Int): Pair<Int, Int> {
    if (inWrap.lines.isEmpty()) return 0 to 0
    val vClamped = inCursor.coerceAtLeast(0)
    // Find the last line whose start is <= cursor.
    var lo = 0; var hi = inWrap.lines.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (inWrap.lineStarts[mid] <= vClamped) lo = mid else hi = mid - 1
    }
    val vLine = lo
    val vCol = (vClamped - inWrap.lineStarts[vLine])
        .coerceIn(0, inWrap.lines[vLine].length)
    return vLine to vCol
}

/* Click position → cursor index, wrap-aware. relY picks the wrapped line
   (clamped to the last), relX picks the column within it. */
private fun charIndexAtWrappedPoint(
    inWrap: WrappedText,
    inFontSize: Int,
    inX: Int,
    inY: Int,
    inLineHeight: Float,
): Int {
    if (inWrap.lines.isEmpty()) return 0
    val vLine = (inY / inLineHeight).toInt().coerceIn(0, inWrap.lines.size - 1)
    val vLineStr = inWrap.lines[vLine]
    val vCol = charIndexAtX(vLineStr, inFontSize, inX)
    return inWrap.lineStarts[vLine] + vCol
}

// ==================
// MARK: Line / column helpers
// ==================

/* Start index of the Nth line (0 → 0; subsequent lines start one past the
   previous newline). For lineIndex past the last line, returns text.length. */
internal fun lineStart(inText: String, inLineIndex: Int): Int {
    if (inLineIndex <= 0) return 0
    var vLine = 0
    for (i in inText.indices) {
        if (inText[i] == '\n') {
            vLine++
            if (vLine == inLineIndex) return i + 1
        }
    }
    return inText.length
}

internal fun lineText(inText: String, inLineIndex: Int): String {
    val vStart = lineStart(inText, inLineIndex)
    val vNl = inText.indexOf('\n', vStart)
    val vEnd = if (vNl < 0) inText.length else vNl
    return inText.substring(vStart, vEnd)
}

internal fun lineCount(inText: String): Int {
    var n = 1
    for (c in inText) if (c == '\n') n++
    return n
}

internal fun lineColumnAt(inText: String, inIndex: Int): Pair<Int, Int> {
    val vClamped = inIndex.coerceIn(0, inText.length)
    var vLine = 0
    var vLineStart = 0
    for (i in 0 until vClamped) {
        if (inText[i] == '\n') {
            vLine++
            vLineStart = i + 1
        }
    }
    return vLine to (vClamped - vLineStart)
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
private const val SCANCODE_RETURN     = 40
private const val SCANCODE_BACKSPACE  = 42
private const val SCANCODE_DOWN       = 81
private const val SCANCODE_RIGHT      = 79
private const val SCANCODE_LEFT       = 80
private const val SCANCODE_UP         = 82
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
    inWrap: WrappedText,
    inFontSize: Int,
    inReadOnly: Boolean,
    inSingleLine: Boolean,
    inGetPrefColX: () -> Int,
    inSetPrefColX: (Int) -> Unit,
    inStructuralEdit: (TextFieldValue) -> Unit,
    inCursorOnlyEdit: (TextFieldValue) -> Unit,
    inTypingEdit: (TextFieldValue) -> Unit,
    inUndo: () -> Unit,
    inRedo: () -> Unit,
): Boolean {
    val vMods = inKey.modifiers
    val vShift = vMods.shift
    val vMeta = vMods.meta
    val vAlt = vMods.alt

    fun resetPrefX() { inSetPrefColX(-1) }
    fun lineStartAt(inLine: Int): Int =
        inWrap.lineStarts.getOrElse(inLine) { inValue.text.length }
    fun lineEndAt(inLine: Int): Int =
        lineStartAt(inLine) + inWrap.lines.getOrElse(inLine) { "" }.length
    fun lineStrAt(inLine: Int): String =
        inWrap.lines.getOrElse(inLine) { "" }

    when (inKey.keyCode) {
        SCANCODE_LEFT -> {
            resetPrefX()
            val vCurrent = inValue.selection.end
            val vNewHead = when {
                vMeta -> lineStartAt(wrappedPosOf(inWrap, vCurrent).first)
                vAlt  -> wordBoundaryLeft(inValue.text, vCurrent)
                !vShift && !inValue.selection.collapsed -> inValue.selection.min
                else  -> vCurrent - 1
            }
            inCursorOnlyEdit(moveCursor(inValue, vNewHead, vShift))
            return true
        }
        SCANCODE_RIGHT -> {
            resetPrefX()
            val vCurrent = inValue.selection.end
            val vNewHead = when {
                vMeta -> lineEndAt(wrappedPosOf(inWrap, vCurrent).first)
                vAlt  -> wordBoundaryRight(inValue.text, vCurrent)
                !vShift && !inValue.selection.collapsed -> inValue.selection.max
                else  -> vCurrent + 1
            }
            inCursorOnlyEdit(moveCursor(inValue, vNewHead, vShift))
            return true
        }
        SCANCODE_UP -> {
            val vCurrent = inValue.selection.end
            val (vLine, vCol) = wrappedPosOf(inWrap, vCurrent)
            if (vLine == 0) {
                inCursorOnlyEdit(moveCursor(inValue, 0, vShift))
                return true
            }
            val vCurX = if (inGetPrefColX() >= 0) inGetPrefColX()
                        else prefixWidth(lineStrAt(vLine), vCol, inFontSize)
            inSetPrefColX(vCurX)
            val vTargetLine = vLine - 1
            val vTargetCol = charIndexAtX(lineStrAt(vTargetLine), inFontSize, vCurX)
            inCursorOnlyEdit(moveCursor(inValue, lineStartAt(vTargetLine) + vTargetCol, vShift))
            return true
        }
        SCANCODE_DOWN -> {
            val vCurrent = inValue.selection.end
            val (vLine, vCol) = wrappedPosOf(inWrap, vCurrent)
            if (vLine >= inWrap.lines.size - 1) {
                inCursorOnlyEdit(moveCursor(inValue, inValue.text.length, vShift))
                return true
            }
            val vCurX = if (inGetPrefColX() >= 0) inGetPrefColX()
                        else prefixWidth(lineStrAt(vLine), vCol, inFontSize)
            inSetPrefColX(vCurX)
            val vTargetLine = vLine + 1
            val vTargetCol = charIndexAtX(lineStrAt(vTargetLine), inFontSize, vCurX)
            inCursorOnlyEdit(moveCursor(inValue, lineStartAt(vTargetLine) + vTargetCol, vShift))
            return true
        }
        SCANCODE_HOME -> {
            resetPrefX()
            val vLine = wrappedPosOf(inWrap, inValue.selection.end).first
            inCursorOnlyEdit(moveCursor(inValue, lineStartAt(vLine), vShift))
            return true
        }
        SCANCODE_END -> {
            resetPrefX()
            val vLine = wrappedPosOf(inWrap, inValue.selection.end).first
            inCursorOnlyEdit(moveCursor(inValue, lineEndAt(vLine), vShift))
            return true
        }
        SCANCODE_RETURN -> if (!inReadOnly && !inSingleLine) {
            inTypingEdit(insertAtCursor(inValue, "\n"))
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
