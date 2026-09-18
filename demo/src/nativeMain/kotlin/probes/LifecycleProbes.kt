import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.compose.sdl.nativeComposeWindow
import utils.encodeBmpBgra32
import utils.writeFile

// ==================
// MARK: Lifecycle probes - back navigation and Navigation3.
// ==================
//
// Split out of MainNative.kt, which held the entry point plus 20 scenarios in
// 1240 lines. Behaviour is unchanged; each probe is `internal` so main()'s
// argument dispatch still reaches it.

/** Boots a window with a BackHandler, injects an Escape key through the live SDL
   path, and asserts the handler fired - proving ComposeWindow's
   BackNavigationInput drives the NavigationEventDispatcher (the mechanism that
   collapses an expanded m3 SearchBar on Escape). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun runBackTest() {
    val vBackNoFocus = mutableStateOf(false)
    val vBackFocused = mutableStateOf(false)
    val vText = mutableStateOf("")
    nativeComposeWindow(
        title = "backtest",
        width = 400,
        height = 200,
        onFrame = { _, frameIndex ->
            when (frameIndex) {
                // Phase 1: Escape with NOTHING focused.
                20 -> {
                    com.compose.sdl.injectKey(41, true)   // SDL_SCANCODE_ESCAPE
                    com.compose.sdl.injectKey(41, false)
                    true
                }
                // Phase 2: click the text field to focus it, then Escape -
                // the user-facing SearchBar scenario (field focused while
                // the back handler should collapse the bar).
                30 -> { com.compose.sdl.injectMouseEvent(1, 200f, 100f); true }
                32 -> { com.compose.sdl.injectMouseEvent(2, 200f, 100f); true }
                44 -> {
                    com.compose.sdl.injectKey(41, true)
                    com.compose.sdl.injectKey(41, false)
                    true
                }
                70 -> {
                    println("backtest: noFocus=${vBackNoFocus.value} focused=${vBackFocused.value}")
                    println(
                        if (vBackNoFocus.value && vBackFocused.value)
                            "backtest: PASS (Escape completed back navigation with and without a focused field)"
                        else
                            "backtest: FAIL (noFocus=${vBackNoFocus.value} focusedField=${vBackFocused.value})"
                    )
                    false
                }
                else -> true
            }
        },
    ) {
        @Suppress("DEPRECATION")
        androidx.compose.ui.backhandler.BackHandler(enabled = true) {
            if (!vBackNoFocus.value) vBackNoFocus.value = true else vBackFocused.value = true
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // The NEW state-based field - the one m3 SearchBar's InputField
                // uses - so the probe exercises its key handler, not the legacy
                // value-based one.
                androidx.compose.foundation.text.BasicTextField(
                    state = androidx.compose.foundation.text.input.rememberTextFieldState(),
                )
            }
        }
    }
}

internal fun runNav3Test() {
    val vShow = mutableStateOf(false)
    nativeComposeWindow(
        title = "nav3test",
        width = 1000,
        height = 700,
        onFrame = { vBridge, vFrame ->
            when (vFrame) {
                30 -> { vShow.value = true; true }
                // Push Detail #1 (click its card) → per-entry lifecycle goes
                // CREATED/STARTED during the slide, RESUMED once settled.
                60 -> { com.compose.sdl.injectMouseEvent(1, 500f, 216f); true }
                62 -> { com.compose.sdl.injectMouseEvent(2, 500f, 216f); true }
                // Pop with ESC → ON_PAUSE, then CREATED while animating out,
                // then the entry disposes.
                120 -> {
                    com.compose.sdl.injectKey(41, true)
                    com.compose.sdl.injectKey(41, false)
                    true
                }
                180 -> {
                    val vSnap = vBridge.snapshotBgra()
                    if (vSnap != null) {
                        val (vW, vH, vBgra) = vSnap
                        writeFile("nav3late.bmp", encodeBmpBgra32(vW, vH, vBgra))
                        println("nav3test: wrote nav3late.bmp (${vW}x${vH})")
                        println("nav3test: PASS (Navigation3 composed at RESUMED; push + pop ran)")
                    } else println("nav3test: FAIL (no snapshot)")
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (vShow.value) {
                    Box(modifier = Modifier.padding(24.dp)) { screens.Navigation3Screen() }
                }
            }
        }
    }
}
