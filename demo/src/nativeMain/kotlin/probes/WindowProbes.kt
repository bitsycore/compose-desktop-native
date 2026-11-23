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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.compose.sdl.Tray
import com.compose.sdl.Window
import com.compose.sdl.nativeComposeApp
import demo.generated.resources.Res
import demo.generated.resources.compose_logo
import org.jetbrains.compose.resources.painterResource
import com.compose.sdl.nativeComposeWindow
import utils.encodeBmpBgra32
import utils.writeFile
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value

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
            state = rememberWindowState(size = DpSize(400.dp, 200.dp)),
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
                state = rememberWindowState(size = DpSize(300.dp, 150.dp)),
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

/** --traytest: a Tray whose icon is a painterResource image, declared BEFORE any window - the
   tray-first / tray-only shape. Image decode used to crash here ("Image decode failed") because
   only a window's render backend installed the decoder. The menu carries checkboxes and a
   submenu so the tray applier builds a real tree. PASS = the app reaches its frame budget. */
internal fun runTrayTest() {
    var vFrames = 0
    nativeComposeApp {
        var vAccepted by remember { mutableStateOf(false) }
        Tray(icon = painterResource(Res.drawable.compose_logo), tooltip = "traytest") {
            CheckboxItem("Accepting checkbox", checked = vAccepted, onCheckedChange = { vAccepted = it })
            CheckboxItem("Rejecting checkbox", checked = false, onCheckedChange = {})
            Separator()
            Menu("Submenu") { Item("Nested item", onClick = {}) }
            Item("Exit", onClick = ::exitApplication)
        }
        Window(onCloseRequest = ::exitApplication, title = "traytest", state = rememberWindowState(size = DpSize(240.dp, 120.dp)),
            onFrame = { _, vFrame -> vFrames = vFrame; vFrame < 10 }) {
            Box(Modifier.fillMaxSize().background(Color(0xFF202020)))
        }
    }
    println(if (vFrames >= 10) "traytest: PASS (painterResource tray icon before any window, menu built)" else "traytest: FAIL (frames=$vFrames)")
}

/** --hiddentest: a Window(visible = false) must get no frames while hidden (Compose Desktop parity:
   skiko only draws a showing layer), then render again once shown. onFrame counts the frames. */
internal fun runHiddenWindowTest() {
    var vHiddenFrames = -1
    var vShownFrames = 0
    nativeComposeApp {
        var vVisible by remember { mutableStateOf(false) }
        var vFrames by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(600)
            vHiddenFrames = vFrames
            vVisible = true
            kotlinx.coroutines.delay(600)
            vShownFrames = vFrames - vHiddenFrames
            exitApplication()
        }
        Window(onCloseRequest = ::exitApplication, title = "hiddentest", state = rememberWindowState(size = DpSize(240.dp, 120.dp)), visible = vVisible,
            onFrame = { _, _ -> vFrames++; true }) {
            Box(Modifier.fillMaxSize().background(Color(0xFF202020)))
        }
    }
    val vPass = vHiddenFrames == 0 && vShownFrames > 0
    println("hiddentest: ${if (vPass) "PASS" else "FAIL"} (frames while hidden=$vHiddenFrames, after show=$vShownFrames)")
}

/** --windowstatetest: Window(state = …) parity with Compose Desktop's WindowState.
   1. size = DpSize(300.dp, Dp.Unspecified) opens (no NaN crash) and the state is written back
      to absolute values (300 x 600 default height);
   2. WindowPosition(Alignment.TopEnd) puts the window frame at the top-right of the screen's
      usable area;
   3. a different WindowState passed on recomposition takes over the window (size applied,
      LocalWindowState follows);
   4. a size write while Maximized does not un-maximize (upstream setSizeSafely);
   5. minimize then un-minimize a maximized window comes back Maximized. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal fun runWindowStateTest() {
    val vResults = mutableListOf<String>()
    var vAllPass = true
    fun check(inName: String, inPass: Boolean, inDetail: String) {
        vAllPass = vAllPass && inPass
        vResults += "  ${if (inPass) "ok  " else "FAIL"} $inName - $inDetail"
    }
    nativeComposeApp {
        val vStateA = remember {
            androidx.compose.ui.window.WindowState(
                size = DpSize(300.dp, androidx.compose.ui.unit.Dp.Unspecified),
                position = androidx.compose.ui.window.WindowPosition(androidx.compose.ui.Alignment.TopEnd),
            )
        }
        val vStateB = remember { androidx.compose.ui.window.WindowState(size = DpSize(500.dp, 400.dp)) }
        var vUseB by remember { mutableStateOf(false) }
        var vWindow by remember { mutableStateOf<com.compose.sdl.ComposeNativeWindow?>(null) }
        var vLocalState by remember { mutableStateOf<androidx.compose.ui.window.WindowState?>(null) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            val vW = vWindow!!
            // 1. Unspecified height
            check("unspecified size", vStateA.size == DpSize(300.dp, 600.dp), "state.size=${vStateA.size}")
            // 2. TopEnd alignment, frame (borders included) vs usable bounds
            val vPos = vStateA.position
            kotlinx.cinterop.memScoped {
                val vSdl = vW.rawSdlHandles().window!!.reinterpret<cnames.structs.SDL_Window>()
                val vBounds = alloc<sdl3.SDL_Rect>()
                val vTop = alloc<kotlinx.cinterop.IntVar>(); val vLeft = alloc<kotlinx.cinterop.IntVar>()
                val vBottom = alloc<kotlinx.cinterop.IntVar>(); val vRight = alloc<kotlinx.cinterop.IntVar>()
                sdl3.SDL_GetWindowBordersSize(vSdl, vTop.ptr, vLeft.ptr, vBottom.ptr, vRight.ptr)
                sdl3.SDL_GetDisplayUsableBounds(sdl3.SDL_GetDisplayForWindow(vSdl), vBounds.ptr)
                val vOk = vPos is androidx.compose.ui.window.WindowPosition.Absolute &&
                    kotlin.math.abs(vPos.x.value + 300 + vRight.value - (vBounds.x + vBounds.w)) <= 2 &&
                    // Frame top at the usable top. macOS: SDL can't report borders
                    // (Cocoa: unsupported, 0) - the OS keeps the title bar below the menu
                    // bar instead, so the client lands one title bar lower.
                    (vPos.y.value - vTop.value - vBounds.y).let { vDy ->
                        if (vTop.value == 0) vDy in -2f..40f else kotlin.math.abs(vDy) <= 2
                    }
                check("TopEnd alignment", vOk,
                    "position=$vPos usable=(${vBounds.x},${vBounds.y} ${vBounds.w}x${vBounds.h}) borders t${vTop.value} l${vLeft.value} b${vBottom.value} r${vRight.value}")
            }
            // 3. State swap
            vUseB = true
            kotlinx.coroutines.delay(800)
            val vLogicalW = (vW.pixelWidth / vW.pixelDensity).toInt()
            check("state swap", vW.state === vStateB && vLocalState === vStateB && vLogicalW == 500,
                "facade.state is B=${vW.state === vStateB}, LocalWindowState is B=${vLocalState === vStateB}, window width=$vLogicalW")
            // 4. Resize while maximized
            vStateB.placement = androidx.compose.ui.window.WindowPlacement.Maximized
            kotlinx.coroutines.delay(1200)
            vStateB.size = DpSize(200.dp, 200.dp)
            kotlinx.coroutines.delay(800)
            val vMaxW = (vW.pixelWidth / vW.pixelDensity).toInt()
            check("size while maximized", vStateB.placement == androidx.compose.ui.window.WindowPlacement.Maximized && vMaxW > 200,
                "placement=${vStateB.placement} window width=$vMaxW")
            // 5. Minimize / un-minimize while maximized
            vStateB.isMinimized = true
            kotlinx.coroutines.delay(1500)
            val vMinimized = vStateB.isMinimized
            vStateB.isMinimized = false
            kotlinx.coroutines.delay(1500)
            check("un-minimize keeps Maximized", vMinimized && !vStateB.isMinimized &&
                vStateB.placement == androidx.compose.ui.window.WindowPlacement.Maximized,
                "minimized=$vMinimized then isMinimized=${vStateB.isMinimized} placement=${vStateB.placement}")
            exitApplication()
        }
        Window(onCloseRequest = ::exitApplication, title = "windowstatetest", state = if (vUseB) vStateB else vStateA) {
            vWindow = window
            vLocalState = com.compose.sdl.LocalWindowState.current
            Box(Modifier.fillMaxSize().background(Color(0xFF202020)))
        }
    }
    println("windowstatetest: ${if (vAllPass) "PASS" else "FAIL"}")
    vResults.forEach(::println)
}
