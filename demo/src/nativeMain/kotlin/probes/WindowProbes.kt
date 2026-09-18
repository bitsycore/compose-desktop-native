import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.compose.sdl.Window
import com.compose.sdl.nativeComposeApp
import com.compose.sdl.nativeComposeWindow
import utils.encodeBmpBgra32
import utils.writeFile

// ==================
// MARK: Window probes - multi-window lifecycle, window info, locale.
// ==================
//
// Split out of MainNative.kt, which held the entry point plus 20 scenarios in
// 1240 lines. Behaviour is unchanged; each probe is `internal` so main()'s
// argument dispatch still reaches it.

/** Boots TWO windows via nativeComposeApp, asserts both render, closes the
   second by flipping the state that composes its Window(), and asserts the app
   keeps running on the first - the multi-window lifecycle end-to-end. */
internal fun runMultiWindowTest() {
    val vShowSecond = mutableStateOf(true)
    var vSecondFrames = 0
    var vResult = "FAIL (main window never reached the end frame)"
    nativeComposeApp {
        Window(
            onCloseRequest = ::exitApplication,
            title = "multiwin main",
            width = 400,
            height = 200,
            onFrame = { _, vFrame ->
                when (vFrame) {
                    40 -> {
                        if (vSecondFrames == 0) {
                            vResult = "FAIL (second window never rendered)"
                            false
                        } else {
                            vShowSecond.value = false  // close the second window via state
                            true
                        }
                    }
                    80 -> {
                        vResult =
                            if (!vShowSecond.value && vSecondFrames > 0)
                                "PASS (both windows rendered; second closed via state; app survived on the first)"
                            else "FAIL (second=$vSecondFrames showSecond=${vShowSecond.value})"
                        false
                    }
                    else -> true
                }
            },
        ) {
            Text("main window", color = Color.White)
        }
        if (vShowSecond.value) {
            Window(
                onCloseRequest = { vShowSecond.value = false },
                title = "multiwin second",
                width = 300,
                height = 150,
                onFrame = { _, _ -> vSecondFrames++; true },
            ) {
                Text("second window", color = Color.White)
            }
        }
    }
    println("multiwintest: secondFrames=$vSecondFrames")
    println("multiwintest: $vResult")
}

/** --windowinfotest: prints LocalWindowInfo.isWindowFocused / containerSize /
   containerDpSize. containerSize starts Zero and becomes the window pixel size
   after the first measure - proof it's fed from the live root constraints. */
internal fun runWindowInfoTest() {
    nativeComposeWindow(
        title = "windowinfotest",
        width = 400,
        height = 300,
        onFrame = { _, vFrame -> vFrame < 6 },
    ) {
        val vWi = androidx.compose.ui.platform.LocalWindowInfo.current
        androidx.compose.runtime.LaunchedEffect(vWi.containerSize, vWi.isWindowFocused) {
            println("windowinfotest: focused=${vWi.isWindowFocused} containerSize=${vWi.containerSize} containerDpSize=${vWi.containerDpSize}")
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
    }
}

/** Boots the REAL Search screen, clicks the first SearchBar's input field
   (expands it), presses Escape, and writes esc_before.bmp / esc_after.bmp -
   the expanded overlay must be visible in `before` and gone in `after`. */
internal fun runSearchEscTest() {
    fun snap(inBridge: com.compose.sdl.RenderBackend, inName: String) {
        val vSnap = inBridge.snapshotBgra() ?: return
        val (vW, vH, vBgra) = vSnap
        writeFile(inName, encodeBmpBgra32(vW, vH, vBgra))
        println("searchesctest: wrote $inName")
    }
    nativeComposeWindow(
        title = "searchesctest",
        width = 1000,
        height = 700,
        onFrame = { vBridge, vFrame ->
            when (vFrame) {
                30 -> { com.compose.sdl.injectMouseEvent(1, 200f, 230f); true }
                32 -> { com.compose.sdl.injectMouseEvent(2, 200f, 230f); true }
                70 -> { snap(vBridge, "esc_before.bmp"); true }
                80 -> {
                    com.compose.sdl.injectKey(41, true)   // Escape
                    com.compose.sdl.injectKey(41, false)
                    true
                }
                130 -> { snap(vBridge, "esc_after.bmp"); false }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val vScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(vScroll)
                    .padding(24.dp),
            ) {
                screens.M3SearchScreen()
            }
        }
    }
}

/* Boots an empty window, then composes screens.Navigation3Screen() at frame 30 -
   AFTER the window lifecycle reached RESUMED - mirroring the real sidebar flow.
   Crashes here (e.g. enableSavedStateHandles' INITIALIZED/CREATED contract) never
   reproduce under --screen, which composes during the initial CREATED composition.
   PASS = a screenshot gets written and the app exits cleanly. */

/** --localetest: prints Locale.current / LocaleList.current (should reflect the OS
   preferred locales via SDL) and screenshots an M3 DatePicker, whose headline and
   navigation labels are M3-translated by Locale.current. Run under a forced locale,
   e.g. `demo.kexe --localetest -AppleLanguages "(fr-FR)"` on macOS. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun runLocaleTest() {
    nativeComposeWindow(
        title = "localetest",
        width = 420,
        height = 560,
        onFrame = { vBridge, vFrame ->
            when (vFrame) {
                2 -> {
                    val vCur = androidx.compose.ui.text.intl.Locale.current
                    val vList = androidx.compose.ui.text.intl.LocaleList.current
                    println("localetest: Locale.current=${vCur.toLanguageTag()}")
                    println("localetest: LocaleList.current=[${vList.localeList.joinToString { it.toLanguageTag() }}]")
                    true
                }
                40 -> {
                    val vSnap = vBridge.snapshotBgra()
                    if (vSnap != null) {
                        val (vW, vH, vBgra) = vSnap
                        writeFile("localetest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                        println("localetest: wrote localetest.bmp (${vW}x${vH})")
                    } else println("localetest: FAIL (no snapshot)")
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
                val vState = androidx.compose.material3.rememberDatePickerState()
                androidx.compose.material3.DatePicker(state = vState)
            }
        }
    }
}
