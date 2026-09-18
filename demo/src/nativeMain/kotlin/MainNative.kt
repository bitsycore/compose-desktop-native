import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.Window
import com.compose.sdl.nativeComposeApp
import demo.registry.allCategories
import demo.shell.App
import screens.ExtraWindows
import utils.encodeBmpBgra32
import utils.parseArgs
import utils.writeFile

// ==================
// MARK: Entry point
// ==================

fun main(args: Array<String>) {
    // Phase 9 B4 probes (`--pipetest=<path.bmp>` / `--inputtest`) were retired
    // during the :ui/:foundation split - they lived in the retired SDL renderer
    // but relied on foundation's Modifier.background / .clickable, which moved
    // to :foundation. Reachable via git history if anyone needs them again.
    if (args.any { it.startsWith("--pipetest") || it == "--inputtest" }) {
        println("[demo] --pipetest / --inputtest were retired in the :foundation split")
        return
    }
    // End-to-end verification of the vendored interaction engine: boots a real
    // window with a clickable box and injects synthetic SDL mouse events through
    // the live path (SDL queue → pollEvents → host.onPointerRaw → processor →
    // upstream clickable's pointerInput gesture coroutine).
    if (args.any { it == "--clicktest" }) {
        runClickTest()
        return
    }
    // Verifies vendored foundation.selection: a real Switch (Modifier.toggleable) flips state
    // when a synthetic click is injected through the live pipeline.
    if (args.any { it == "--toggletest" }) {
        runToggleTest()
        return
    }
    // Verifies key + text routing: a focused node receives injected SDL TEXT_INPUT
    // and KEY events through the FocusOwner (BasicTextField's typed-events path).
    if (args.any { it == "--keytest" }) {
        runKeyTest()
        return
    }
    // Verifies the Escape→back pipeline: an unconsumed Escape completes a back
    // navigation on the window's NavigationEventDispatcher (BackHandler fires).
    if (args.any { it == "--backtest" }) {
        runBackTest()
        return
    }
    // Live-screen variant: boots the real Search screen, clicks the SearchBar
    // to expand it, presses Escape, and dumps before/after screenshots.
    if (args.any { it == "--searchesctest" }) {
        runSearchEscTest()
        return
    }
    // Verifies the Compose Desktop window attributes (undecorated / resizable /
    // alwaysOnTop / focusable / enabled) reach SDL, plus the raw SDL escape hatch.
    if (args.any { it == "--winattrtest" }) {
        runWindowAttrTest()
        return
    }
    // Verifies nativeComposeApp multi-window: two Windows render concurrently,
    // one closes via state, the app keeps running on the survivor.
    if (args.any { it == "--multiwintest" }) {
        runMultiWindowTest()
        return
    }
    // Verifies the lookahead pass: a SharedTransitionLayout shared-element
    // morph runs both directions without crashing.
    if (args.any { it == "--sharedtest" }) {
        runSharedTest()
        return
    }
    // Verifies the vendored scroll system: a Column(verticalScroll) scrolls when wheel
    // events are injected through the live pipeline (MouseWheelScrollingLogic).
    if (args.any { it == "--scrolltest" }) {
        runScrollTest()
        return
    }
    // Verifies the vendored text-paragraph engine: builds a real upstream Paragraph (SkiaParagraph)
    // and checks width-wrapping + offset<->position geometry.
    if (args.any { it == "--paragraphtest" }) {
        runParagraphTest()
        return
    }
    // Prints Paragraph cell/line metrics for a size sweep - compare against the JVM
    // leg's `--metrics` output to align the native text metrics with upstream (P3.1).
    if (args.any { it == "--metricsprobe" }) {
        runMetricsProbe()
        return
    }
    // Verifies the Dialog appearance animation (upstream Dialog.skiko.kt parity):
    // opens a real m3 AlertDialog via an injected click, dumps mid-animation and
    // settled screenshots - the mid shots must show the dialog fainter and lower.
    if (args.any { it == "--dialoganimtest" }) {
        runDialogAnimTest()
        return
    }
    // Traces AnimatedVisibility(fade+expand/shrink) frame-by-frame: the AV
    // container's animated size + the Y of a marker below it, across three
    // toggles - diagnoses end-of-animation size snaps / instant transitions.
    if (args.any { it == "--animvistest" }) {
        runAnimVisTest()
        return
    }
    // Composes the Navigation3 screen LATE (window already RESUMED) - the
    // normal sidebar flow, unlike --screen which composes during the first
    // (CREATED) composition. Guards the enableSavedStateHandles contract:
    // ViewModel store owners created at RESUMED must opt out of saved state.
    if (args.any { it == "--nav3test" }) {
        runNav3Test()
        return
    }
    if (args.any { it == "--soaktest" }) {
        runSoakTest()
        return
    }
    if (args.any { it == "--localetest" }) {
        runLocaleTest()
        return
    }
    if (args.any { it == "--cursortest" }) {
        runCursorTest()
        return
    }
    if (args.any { it == "--windowinfotest" }) {
        runWindowInfoTest()
        return
    }
    if (args.any { it == "--imetest" }) {
        runImeTest()
        return
    }
    if (args.any { it == "--imagebytestest" }) {
        runImageBytesTest()
        return
    }
    if (args.any { it == "--fonttest" }) {
        runFontTest()
        return
    }

    val vCli = parseArgs(args)
    val vTitle = buildString {
        append("ComposeDesktopNative Showcase")
        if (vCli.screen != null) append(" - ").append(vCli.screen)
        append(" [").append(vCli.gpu).append("]")
    }

    // Screenshot runs freeze infinite animations (rememberInfiniteTransition & co. cancel
    // at their initial value) so every screen can reach quiescence, and step the frame
    // clocks on VIRTUAL time (16.6ms/frame, like the JVM leg's render(nanos)) so animation
    // races resolve identically every run - both must be set before the first window composes.
    if (vCli.screenshot != null) {
        com.compose.sdl.disableInfiniteAnimations = true
        com.compose.sdl.useVirtualFrameTime = true
    }

    // Multi-window app shell: the showcase window plus any extra windows opened
    // from WindowScreen's "Multi-window" section (state-driven, Compose Desktop
    // style - the count IS the windows' lifetime).
    nativeComposeApp {
    Window(
        onCloseRequest = { exitApplication() },
        title = vTitle,
        width = vCli.width,
        height = vCli.height,
        gpu = vCli.gpu,
        onFrame = if (vCli.screenshot != null) {
            // P0.5 render-to-quiescence: capture once the window reports no pending
            // invalidations for a few consecutive frames (entrance animations settled,
            // async loads applied), or at the --frames cap as a safety net. Replaces
            // the fixed frame-6 capture, whose mid-animation timing was run-dependent.
            var vQuietFrames = 0
            { bridge, frameIndex ->
                vQuietFrames = if (com.compose.sdl.windowHasInvalidations()) 0 else vQuietFrames + 1
                if (vQuietFrames >= 3 || frameIndex >= vCli.maxFrames) {
                    if (frameIndex >= vCli.maxFrames) {
                        println("Screenshot: quiescence not reached by frame $frameIndex - capturing anyway")
                    }
                    val vSnap = bridge.snapshotBgra()
                    if (vSnap != null) {
                        val (vW, vH, vBgra) = vSnap
                        val vBmp = encodeBmpBgra32(vW, vH, vBgra)
                        writeFile(vCli.screenshot, vBmp)
                        println("Wrote screenshot: ${vCli.screenshot} (${vW}x${vH}, settled at frame $frameIndex)")
                    } else println("Screenshot snapshot was null")
                    false  // quit
                } else true
            }
        } else null,
    ) {
        // Material Symbols fonts auto-install on first use of the matching
        // MaterialSymbolsOutlined / Rounded / Sharp composable - no setup
        // needed here. Apps that want to preload the bytes at startup can
        // still call .install() explicitly.
        MaterialTheme(colorScheme = darkColorScheme()) {
            if (vCli.screen != null) {
                val vAllScreens = allCategories().flatMap { it.screens }
                val vMatch = vAllScreens.firstOrNull { it.name.equals(vCli.screen, ignoreCase = true) }
                if (vMatch == null) {
                    println("Unknown --screen='${vCli.screen}'. Available: ${vAllScreens.joinToString { it.name }}")
                    Text("Unknown screen: ${vCli.screen}", color = Color.Red, fontSize = 16.sp)
                } else {
                    // Single screen, no sidebar - SAME wrapper as the App's
                    // content pane (verticalScroll ⇒ infinite max height!) so
                    // screenshot verification exercises the constraints screens
                    // actually get when navigated to interactively. A plain
                    // bounded Box here used to hide "scrollable measured with
                    // infinite height" crashes from the --screenshot sweeps.
                    val vScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(vScroll)
                            .padding(24.dp),
                    ) {
                        vMatch.content()
                    }
                }
            } else {
                App()
            }
        }
    }
    // Extra windows opened from WindowScreen - one Window() per id, keyed by id so
    // closing one (OS button or its Close button) removes exactly THAT window.
    for (vId in ExtraWindows) {
        key(vId) {
            Window(
                onCloseRequest = { ExtraWindows.remove(vId) },
                title = "Extra window $vId",
                width = 460,
                height = 300,
                gpu = vCli.gpu,
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    ExtraWindowContent(vId)
                }
            }
        }
    }
    }
}

/** Content of the demo's extra windows - each has its own composition, focus,
   input routing, and render loop; the counter proves per-window state. */
@Composable
private fun com.compose.sdl.ComposeWindowScope.ExtraWindowContent(inId: Int) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Extra window #$inId", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
            Text(
                "own composition · own renderer · own input",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            var vClicks by remember { mutableStateOf(0) }
            androidx.compose.material3.Button(onClick = { vClicks++ }) { Text("clicks: $vClicks") }
            androidx.compose.material3.OutlinedButton(onClick = { window.close() }) { Text("Close window") }
        }
    }
}
