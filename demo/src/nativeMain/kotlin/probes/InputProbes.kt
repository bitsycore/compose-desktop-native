import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.nativeComposeWindow

// ==================
// MARK: Input probes - pointer, keyboard, wheel, cursor and IME.
// ==================
//
// Split out of MainNative.kt, which held the entry point plus 20 scenarios in
// 1240 lines. Behaviour is unchanged; each probe is `internal` so main()'s
// argument dispatch still reaches it.

/** Boots a real nativeComposeWindow with a full-size clickable box, then injects
   move→press→release SDL mouse events a few frames apart (giving the upstream
   clickable's gesture coroutine time to launch + await between frames), and prints
   PASS/FAIL based on whether onClick fired. Proves the whole vendored interaction
   pipeline works under the real Sdl3MainDispatcher + frame loop. */
internal fun runClickTest() {
    var vClicks = 0
    nativeComposeWindow(
        title = "clicktest",
        width = 400,
        height = 300,
        onFrame = { _, frameIndex ->
            when (frameIndex) {
                20 -> { com.compose.sdl.injectMouseEvent(0, 200f, 150f); true }
                26 -> { com.compose.sdl.injectMouseEvent(1, 200f, 150f); true }
                32 -> { com.compose.sdl.injectMouseEvent(2, 200f, 150f); true }
                70 -> {
                    println(
                        if (vClicks > 0) "clicktest: PASS ($vClicks click(s) via upstream clickable)"
                        else "clicktest: FAIL (0 clicks - upstream clickable did not fire)"
                    )
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF303040))
                    .clickable { vClicks++; println("clicktest: onClick fired -> $vClicks") },
            ) {
                Text("Click test", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

/** Same injection harness as clicktest, but the target is a Material Switch (Modifier.toggleable
   from the vendored foundation.selection). Asserts onCheckedChange flipped the state - proving
   toggleable rides the same verified upstream interaction path as clickable. */
internal fun runToggleTest() {
    var vChecked = false
    var vChanges = 0
    nativeComposeWindow(
        title = "toggletest",
        width = 400,
        height = 300,
        onFrame = { _, frameIndex ->
            when (frameIndex) {
                20 -> { com.compose.sdl.injectMouseEvent(0, 200f, 150f); true }
                26 -> { com.compose.sdl.injectMouseEvent(1, 200f, 150f); true }
                32 -> { com.compose.sdl.injectMouseEvent(2, 200f, 150f); true }
                70 -> {
                    println(
                        if (vChanges > 0 && vChecked) "toggletest: PASS (switch toggled to $vChecked via toggleable)"
                        else "toggletest: FAIL (changes=$vChanges checked=$vChecked)"
                    )
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Switch(
                    checked = vChecked,
                    onCheckedChange = { vChecked = it; vChanges++; println("toggletest: onCheckedChange -> $it") },
                )
            }
        }
    }
}

/** Boots a window with a real BasicTextField, clicks it to focus (focus-on-click via the
   FocusOwner), then injects TEXT_INPUT ("A","B") and a Backspace key through the live SDL path.
   Asserts the field edits to "A" - proving click-to-focus + typing + editing keys route to the
   focused field via ComposeRootHost.dispatchKeyEvent + the synthesised typed-key path. */
internal fun runKeyTest() {
    val vText = mutableStateOf("")
    nativeComposeWindow(
        title = "keytest",
        width = 400,
        height = 200,
        onFrame = { _, frameIndex ->
            when (frameIndex) {
                12 -> { com.compose.sdl.injectMouseEvent(1, 200f, 100f); true } // click field to focus
                14 -> { com.compose.sdl.injectMouseEvent(2, 200f, 100f); true }
                24 -> { com.compose.sdl.injectTextInput("A"); true }
                28 -> { com.compose.sdl.injectTextInput("B"); true }
                32 -> { com.compose.sdl.injectKey(42, true); com.compose.sdl.injectKey(42, false); true } // Backspace → "A"
                70 -> {
                    println("keytest: real BasicTextField value='${vText.value}'")
                    println(
                        if (vText.value == "A") "keytest: PASS (click-to-focus + type 'AB' + backspace = 'A')"
                        else "keytest: FAIL (expected 'A')"
                    )
                    false
                }
                else -> true
            }
        },
    ) {
        // The exact regression path: a real BasicTextField, focused by clicking it,
        // receiving typed text (SDL TEXT_INPUT) + editing keys (Backspace).
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.text.BasicTextField(
                    value = vText.value,
                    onValueChange = { vText.value = it },
                )
            }
        }
    }
}

/** Boots a tall Column wrapped in the vendored Modifier.verticalScroll, injects several wheel-down
   events through the live SDL path (→ processor → MouseWheelScrollingLogic), and asserts the
   ScrollState offset advanced - proving upstream scrolling works end-to-end. */
internal fun runScrollTest() {
    val vScroll = androidx.compose.foundation.ScrollState(0)
    nativeComposeWindow(
        title = "scrolltest",
        width = 400,
        height = 300,
        onFrame = { _, frameIndex ->
            when {
                frameIndex in 20..40 && frameIndex % 2 == 0 -> {
                    com.compose.sdl.injectWheel(200f, 150f, 0f, -3f) // wheel down
                    true
                }
                frameIndex == 90 -> {
                    println("scrolltest: ScrollState.value=${vScroll.value} maxValue=${vScroll.maxValue}")
                    println(
                        if (vScroll.value > 0) "scrolltest: PASS (scrolled to ${vScroll.value}px)"
                        else "scrolltest: FAIL (did not scroll)"
                    )
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(vScroll)) {
                repeat(40) { i ->
                    Text("Scroll row $i - lorem ipsum dolor sit", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

/** --cursortest: injects hover moves over pointerHoverIcon(Text) / (Hand) regions
   and a bare background, printing the applied SDL system cursor after each. Proves
   PointerIcon -> SDL_SetCursor end-to-end through the live hover pipeline. */
internal fun runCursorTest() {
    var vBg = "?"; var vText = "?"; var vHand = "?"
    nativeComposeWindow(
        title = "cursortest",
        width = 400,
        height = 400,
        onFrame = { _, vFrame ->
            when (vFrame) {
                10 -> { com.compose.sdl.injectMouseEvent(0, 200f, 300f); true } // background (below both boxes)
                14 -> { vBg = com.compose.sdl.appliedCursorName(); true }
                20 -> { com.compose.sdl.injectMouseEvent(0, 200f, 50f); true }  // Text box
                24 -> { vText = com.compose.sdl.appliedCursorName(); true }
                30 -> { com.compose.sdl.injectMouseEvent(0, 200f, 150f); true } // Hand box
                34 -> {
                    vHand = com.compose.sdl.appliedCursorName()
                    println("cursortest: background=$vBg text=$vText hand=$vHand")
                    val vOk = vBg.endsWith("DEFAULT") && vText.endsWith("TEXT") && vHand.endsWith("POINTER")
                    println(if (vOk) "cursortest: PASS" else "cursortest: FAIL")
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxWidth().height(100.dp).pointerHoverIcon(PointerIcon.Text))
                Box(Modifier.fillMaxWidth().height(100.dp).pointerHoverIcon(PointerIcon.Hand))
            }
        }
    }
}

/** --imetest: focuses a BasicTextField, injects an IME composition (SDL TEXT_EDITING
   "ni"), then a commit (SDL TEXT_INPUT "に"). Verifies the preedit shows a
   composing region and the commit REPLACES it (not appends) - the real IME path. */
internal fun runImeTest() {
    val vField = mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    nativeComposeWindow(
        title = "imetest",
        width = 500,
        height = 200,
        onFrame = { _, vFrame ->
            when (vFrame) {
                12 -> { com.compose.sdl.injectMouseEvent(1, 250f, 100f); true } // focus centered field
                14 -> { com.compose.sdl.injectMouseEvent(2, 250f, 100f); true }
                34 -> { com.compose.sdl.injectTextEditing("ni"); true }         // composing (preedit)
                40 -> { println("imetest: composing text='${vField.value.text}' composition=${vField.value.composition}"); true }
                50 -> { com.compose.sdl.injectTextInput("に"); true }       // commit "に"
                60 -> {
                    println("imetest: committed text='${vField.value.text}' composition=${vField.value.composition}")
                    val vOk = vField.value.text == "に" && vField.value.composition == null
                    println(if (vOk) "imetest: PASS" else "imetest: FAIL")
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = vField.value,
                    onValueChange = { vField.value = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 24.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                )
            }
        }
    }
}
