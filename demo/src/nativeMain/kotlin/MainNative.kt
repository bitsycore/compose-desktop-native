import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.Window
import com.compose.sdl.nativeComposeApp
import com.compose.sdl.nativeComposeWindow
import demo.registry.allCategories
import demo.shell.App
import screens.ExtraWindows
import utils.encodeBmpBgra32
import utils.parseArgs
import utils.writeFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString

// ==================
// MARK: Entry point
// ==================

fun main(args: Array<String>) {
    // Phase 9 B4 probes (`--pipetest=<path.bmp>` / `--inputtest`) were retired
    // during the :core/:foundation split — they lived in :core's sdlRendererMain
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
    // Verifies the vendored text-paragraph engine: builds a real upstream Paragraph (bridged to the
    // SDL TextMeasurer via SdlParagraph) and checks width-wrapping + offset<->position geometry.
    if (args.any { it == "--paragraphtest" }) {
        runParagraphTest()
        return
    }
    // Prints Paragraph cell/line metrics for a size sweep — compare against the JVM
    // leg's `--metrics` output to align the SDL text metrics with upstream (P3.1).
    if (args.any { it == "--metricsprobe" }) {
        runMetricsProbe()
        return
    }
    // Verifies the Dialog appearance animation (upstream Dialog.skiko.kt parity):
    // opens a real m3 AlertDialog via an injected click, dumps mid-animation and
    // settled screenshots — the mid shots must show the dialog fainter and lower.
    if (args.any { it == "--dialoganimtest" }) {
        runDialogAnimTest()
        return
    }
    // Traces AnimatedVisibility(fade+expand/shrink) frame-by-frame: the AV
    // container's animated size + the Y of a marker below it, across three
    // toggles — diagnoses end-of-animation size snaps / instant transitions.
    if (args.any { it == "--animvistest" }) {
        runAnimVisTest()
        return
    }
    // Composes the Navigation3 screen LATE (window already RESUMED) — the
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
    if (args.any { it == "--dashtest" }) {
        runDashTest()
        return
    }
    if (args.any { it == "--tilemodetest" }) {
        runTileModeTest()
        return
    }
    if (args.any { it == "--fonttest" }) {
        runFontTest()
        return
    }
    if (args.any { it == "--rotimgtest" }) {
        runRotImgTest()
        return
    }
    if (args.any { it == "--pointstest" }) {
        runPointsTest()
        return
    }
    if (args.any { it == "--blendtest" }) {
        runBlendTest()
        return
    }
    if (args.any { it == "--jointest" }) {
        runJoinTest()
        return
    }
    if (args.any { it == "--filtertest" }) {
        runFilterTest()
        return
    }

    val vCli = parseArgs(args)
    val vTitle = buildString {
        append("ComposeDesktopNative Showcase")
        if (vCli.screen != null) append(" — ").append(vCli.screen)
        append(" [").append(vCli.gpu).append("]")
    }

    // Screenshot runs freeze infinite animations (rememberInfiniteTransition & co. cancel
    // at their initial value) so every screen can reach quiescence, and step the frame
    // clocks on VIRTUAL time (16.6ms/frame, like the JVM leg's render(nanos)) so animation
    // races resolve identically every run — both must be set before the first window composes.
    if (vCli.screenshot != null) {
        com.compose.sdl.disableInfiniteAnimations = true
        com.compose.sdl.useVirtualFrameTime = true
    }

    // Multi-window app shell: the showcase window plus any extra windows opened
    // from WindowScreen's "Multi-window" section (state-driven, Compose Desktop
    // style — the count IS the windows' lifetime).
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
        // MaterialSymbolsOutlined / Rounded / Sharp composable — no setup
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
                    // Single screen, no sidebar — SAME wrapper as the App's
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
    // Extra windows opened from WindowScreen — one Window() per id, keyed by id so
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

/* Content of the demo's extra windows — each has its own composition, focus,
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

/* Boots a window with a BackHandler, injects an Escape key through the live SDL
   path, and asserts the handler fired — proving ComposeWindow's
   BackNavigationInput drives the NavigationEventDispatcher (the mechanism that
   collapses an expanded m3 SearchBar on Escape). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun runBackTest() {
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
                // Phase 2: click the text field to focus it, then Escape —
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
                // The NEW state-based field — the one m3 SearchBar's InputField
                // uses — so the probe exercises its key handler, not the legacy
                // value-based one.
                androidx.compose.foundation.text.BasicTextField(
                    state = androidx.compose.foundation.text.input.rememberTextFieldState(),
                )
            }
        }
    }
}

/* Drives a SharedTransitionLayout shared-element morph both directions from
   the frame counter (no clicks) — it requires the LOOKAHEAD pass, which dies
   with "LookaheadDelegate has not been measured yet" if the owner drops
   affectsLookahead measure/relayout requests. Reaching the end frame = PASS. */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
private fun runSharedTest() {
    val vBig = mutableStateOf(false)
    nativeComposeWindow(
        title = "sharedtest",
        width = 300,
        height = 300,
        onFrame = { _, vFrame ->
            when (vFrame) {
                20 -> { vBig.value = true; true }    // small → big morph
                70 -> { vBig.value = false; true }   // big → small morph
                130 -> {
                    println("sharedtest: PASS (shared-element morph ran both directions)")
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.animation.SharedTransitionLayout {
                Column {
                    androidx.compose.animation.AnimatedVisibility(visible = !vBig.value) {
                        Box(
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "box"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                )
                                .size(48.dp)
                                .background(Color(0xFF7C4DFF), RoundedCornerShape(8.dp)),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = vBig.value) {
                        Box(
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "box"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                )
                                .size(160.dp)
                                .background(Color(0xFF26A69A), RoundedCornerShape(24.dp)),
                        )
                    }
                }
            }
        }
    }
}

/* Boots TWO windows via nativeComposeApp, asserts both render, closes the
   second by flipping the state that composes its Window(), and asserts the app
   keeps running on the first — the multi-window lifecycle end-to-end. */
private fun runMultiWindowTest() {
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

/* Boots the REAL Search screen, clicks the first SearchBar's input field
   (expands it), presses Escape, and writes esc_before.bmp / esc_after.bmp —
   the expanded overlay must be visible in `before` and gone in `after`. */
private fun runSearchEscTest() {
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

/* Boots an empty window, then composes screens.Navigation3Screen() at frame 30 —
   AFTER the window lifecycle reached RESUMED — mirroring the real sidebar flow.
   Crashes here (e.g. enableSavedStateHandles' INITIALIZED/CREATED contract) never
   reproduce under --screen, which composes during the initial CREATED composition.
   PASS = a screenshot gets written and the app exits cleanly. */
/* P2.2 soak — cycle through EVERY registered screen kCycles times in ONE process,
   disposing each via a changing key() so composition + layer (skiko RenderNode / SDL
   node) allocate-and-release is exercised repeatedly. After each full cycle, GC then
   record peak RSS (getrusage ru_maxrss). Peak RSS is monotonic, so after cycle 1 it
   already reflects visiting every screen once; if there is NO leak it plateaus, if
   there IS one it keeps climbing each cycle. PASS iff last-cycle peak stays within a
   ceiling of the first-cycle peak. Runs on both renderer legs. */
@OptIn(kotlin.native.runtime.NativeRuntimeApi::class, ExperimentalForeignApi::class)
private fun runSoakTest() {
    // Disable never-settling animations so RSS reflects composition/layer lifetime,
    // not live animation-state churn (same seed the screenshot path uses).
    com.compose.sdl.disableInfiniteAnimations = true
    com.compose.sdl.useVirtualFrameTime = true
    val vAll = allCategories().flatMap { it.screens }
    // CDN_SOAK_SCREEN=<name> repeats ONE screen 40x/cycle (bisect a specific screen);
    // default cycles through every screen.
    val vTarget = platform.posix.getenv("CDN_SOAK_SCREEN")?.toKString()
    val vScreens = if (vTarget != null) { val vS = vAll.first { it.name.equals(vTarget, ignoreCase = true) }; List(40) { vS } }
        else vAll
    val vIndex = mutableStateOf(0)
    // CDN_SOAK_STATIC=1: mount ONE screen once, never remount — measure RSS every 120 frames.
    // Isolates a per-FRAME leak (RSS climbs with no remounts) from a per-MOUNT leak.
    val vStatic = platform.posix.getenv("CDN_SOAK_STATIC")?.toKString() == "1"
    val kFramesPerScreen = if (vStatic) 120 else 3
    val kCycles = platform.posix.getenv("CDN_SOAK_CYCLES")?.toKString()?.toIntOrNull() ?: 3
    val vRssPerCycleMb = mutableListOf<Long>()
    var vShown = 0

    nativeComposeWindow(
        title = "soaktest",
        width = 1000,
        height = 700,
        onFrame = { _, vFrame ->
            if (vFrame > 0 && vFrame % kFramesPerScreen == 0) {
                if (vStatic) {
                    // No remount — just pump frames on the one mounted screen.
                    kotlin.native.runtime.GC.collect()
                    vRssPerCycleMb.add(currentResidentMb())
                    println("soaktest[static]: measure ${vRssPerCycleMb.size}: currentRSS=${vRssPerCycleMb.last()}MB")
                } else {
                    vShown++
                    vIndex.value = vShown % vScreens.size
                    if (vShown % vScreens.size == 0) {
                        kotlin.native.runtime.GC.collect()
                        vRssPerCycleMb.add(currentResidentMb())
                        println("soaktest: cycle ${vRssPerCycleMb.size}: currentRSS=${vRssPerCycleMb.last()}MB")
                    }
                }
            }
            if (vRssPerCycleMb.size >= kCycles) {
                val vFirst = vRssPerCycleMb.first()
                val vLast = vRssPerCycleMb.last()
                val vGrowth = vLast - vFirst
                val vCeiling = maxOf(48L, vFirst / 4)  // 25% or 48MB, whichever larger
                println("soaktest: currentRSS/cycle(MB)=$vRssPerCycleMb (${vScreens.size} screens x $kCycles cycles)")
                if (vGrowth <= vCeiling) {
                    println("soaktest: PASS (current RSS grew ${vGrowth}MB over cycles 1->$kCycles, within ${vCeiling}MB ceiling)")
                } else {
                    println("soaktest: FAIL (current RSS grew ${vGrowth}MB > ${vCeiling}MB ceiling — possible leak)")
                }
                false
            } else true
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
                // key(vShown) forces a FULL dispose+recompose each advance (even when the
                // target screen repeats) — the allocate/release churn we soak. Reading
                // vIndex.value (a State) is what triggers the recomposition each mount.
                key(vShown) {
                    vScreens[vIndex.value].content()
                }
            }
        }
    }
}

/* CURRENT resident set (MB): current RSS can DROP after GC, so it distinguishes a
   true leak (ratchets up) from K/N allocator high-water, unlike getrusage's
   monotonic peak. posix reads it from `ps`; mingw has no `ps`/popen and returns
   -1 (the soak gate runs on macOS/Linux — see scripts/verify-mac.sh). */
internal expect fun currentResidentMb(): Long

/* --localetest: prints Locale.current / LocaleList.current (should reflect the OS
   preferred locales via SDL) and screenshots an M3 DatePicker, whose headline and
   navigation labels are M3-translated by Locale.current. Run under a forced locale,
   e.g. `demo.kexe --localetest -AppleLanguages "(fr-FR)"` on macOS. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun runLocaleTest() {
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

/* --cursortest: injects hover moves over pointerHoverIcon(Text) / (Hand) regions
   and a bare background, printing the applied SDL system cursor after each. Proves
   PointerIcon -> SDL_SetCursor end-to-end through the live hover pipeline. */
private fun runCursorTest() {
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

/* --windowinfotest: prints LocalWindowInfo.isWindowFocused / containerSize /
   containerDpSize. containerSize starts Zero and becomes the window pixel size
   after the first measure — proof it's fed from the live root constraints. */
private fun runWindowInfoTest() {
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

/* --imetest: focuses a BasicTextField, injects an IME composition (SDL TEXT_EDITING
   "ni"), then a commit (SDL TEXT_INPUT "に"). Verifies the preedit shows a
   composing region and the commit REPLACES it (not appends) — the real IME path. */
private fun runImeTest() {
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

/* --imagebytestest: decodes an in-memory 2x2 BMP via ByteArray.decodeToImageBitmap()
   (routes to createImageBitmap(bytes)). On the SDL leg this used to throw
   UnsupportedOperationException; now it decodes through SDL3_image. Needs a window
   so the render backend installs the encoded-image decoder. */
private fun runImageBytesTest() {
    nativeComposeWindow(
        title = "imagebytestest",
        width = 200,
        height = 200,
        onFrame = { _, vFrame ->
            if (vFrame >= 2) {
                try {
                    val vBitmap = tinyRedBmp().decodeToImageBitmap()
                    println("imagebytestest: decoded ${vBitmap.width}x${vBitmap.height}")
                    println(if (vBitmap.width == 2 && vBitmap.height == 2) "imagebytestest: PASS" else "imagebytestest: FAIL (wrong size)")
                } catch (e: Throwable) {
                    println("imagebytestest: FAIL (${e::class.simpleName}: ${e.message})")
                }
                false
            } else true
        },
    ) {}
}

/* --fonttest: same sample in FontFamily.Default (sans) and FontFamily.Monospace.
   Monospace used to collapse to the default sans; now it renders NotoSansMono
   (bundled because this source references FontFamily.Monospace). */
private fun runFontTest() {
    nativeComposeWindow(
        title = "fonttest",
        width = 560,
        height = 220,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 10) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("fonttest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("fonttest: wrote fonttest.bmp (${vW}x${vH})")
                }
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF202020)).padding(20.dp)) {
                val vSample = "Illegal1 lIO0 {}=>"
                Text(vSample, color = Color.White, fontSize = 30.sp)
                Text(
                    vSample, color = Color.Cyan, fontSize = 30.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}

/* --tilemodetest: three rows filled with a narrow red->blue linear gradient using
   TileMode Repeated / Mirror / Decal. On the SDL leg TileMode was dropped (all
   clamped); now they tile / reflect / cut off (transparent) respectively. */
private fun runTileModeTest() {
    nativeComposeWindow(
        title = "tilemodetest",
        width = 400,
        height = 320,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 20) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("tilemodetest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("tilemodetest: wrote tilemodetest.bmp (${vW}x${vH})")
                }
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF102030))) {
                val vColors = listOf(Color.Red, Color.Blue)
                for (vMode in listOf(
                    androidx.compose.ui.graphics.TileMode.Repeated,
                    androidx.compose.ui.graphics.TileMode.Mirror,
                    androidx.compose.ui.graphics.TileMode.Decal,
                )) {
                    Box(
                        Modifier.fillMaxWidth().height(96.dp).padding(8.dp).background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = vColors,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(70f, 0f),
                                tileMode = vMode,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/* --dashtest: draws dashed lines + a dashed stroked rect on a Canvas and
   screenshots. On the SDL leg these used to render solid; now they dash. */
private fun runDashTest() {
    nativeComposeWindow(
        title = "dashtest",
        width = 400,
        height = 300,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 20) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("dashtest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("dashtest: wrote dashtest.bmp (${vW}x${vH})")
                } else println("dashtest: FAIL (no snapshot)")
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(Color(0xFF202020))) {
                val vDash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(22f, 12f), 0f)
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(20f, 50f), androidx.compose.ui.geometry.Offset(size.width - 20f, 50f), strokeWidth = 6f, pathEffect = vDash)
                drawLine(Color.Cyan, androidx.compose.ui.geometry.Offset(20f, 110f), androidx.compose.ui.geometry.Offset(size.width - 20f, 260f), strokeWidth = 6f, pathEffect = vDash)
                val vPath = androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(40f, 150f, size.width - 40f, size.height - 20f))
                }
                drawPath(vPath, Color.Yellow, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, pathEffect = vDash))
            }
        }
    }
}

/* --pointstest: drawPoints in all three PointModes (Points / Lines / Polygon).
   These were no-ops on the SDL leg (charts/scatter rendered nothing); now they draw. */
private fun runPointsTest() {
    nativeComposeWindow(
        title = "pointstest",
        width = 420,
        height = 320,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 10) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("pointstest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("pointstest: wrote pointstest.bmp (${vW}x${vH})")
                }
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(Color(0xFF202020))) {
                fun off(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
                val vPoints = (0..8).map { off(40f + it * 42f, 70f) }
                drawPoints(vPoints, androidx.compose.ui.graphics.PointMode.Points, Color.Cyan, strokeWidth = 12f)
                val vLines = (0..7).map { off(40f + it * 48f, 160f + (it % 2) * 46f) }
                drawPoints(vLines, androidx.compose.ui.graphics.PointMode.Lines, Color.Yellow, strokeWidth = 5f)
                val vPoly = (0..8).map { off(40f + it * 42f, 260f + kotlin.math.sin(it.toFloat()) * 24f) }
                drawPoints(vPoly, androidx.compose.ui.graphics.PointMode.Polygon, Color.Magenta, strokeWidth = 5f)
            }
        }
    }
}

/* --blendtest: three overlapping circles (R/G/B) drawn with BlendMode.Plus, plus a
   Multiply and a Modulate pair. On the SDL leg blend modes were ignored (all SrcOver);
   now Plus overlaps brighten toward white and Multiply darkens. */
private fun runBlendTest() {
    nativeComposeWindow(
        title = "blendtest",
        width = 420,
        height = 320,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 12) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("blendtest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("blendtest: wrote blendtest.bmp (${vW}x${vH})")
                } else println("blendtest: FAIL (no snapshot)")
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(Color(0xFF101010))) {
                fun off(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
                // Additive: R + G + B overlaps -> white at the centre.
                drawCircle(Color.Red, radius = 60f, center = off(120f, 100f), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                drawCircle(Color.Green, radius = 60f, center = off(170f, 100f), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                drawCircle(Color.Blue, radius = 60f, center = off(145f, 150f), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                // Multiply: yellow over cyan -> green intersection.
                drawRect(Color.Yellow, topLeft = off(260f, 40f), size = androidx.compose.ui.geometry.Size(120f, 120f))
                drawRect(Color.Cyan, topLeft = off(300f, 80f), size = androidx.compose.ui.geometry.Size(120f, 120f), blendMode = androidx.compose.ui.graphics.BlendMode.Multiply)
                // Modulate: white over a gradient-ish grey scales it down.
                drawRect(Color(0xFF808080), topLeft = off(40f, 230f), size = androidx.compose.ui.geometry.Size(160f, 60f))
                drawRect(Color(0xFFC00000), topLeft = off(80f, 230f), size = androidx.compose.ui.geometry.Size(160f, 60f), blendMode = androidx.compose.ui.graphics.BlendMode.Modulate)
            }
        }
    }
}

/* --filtertest: exercises ColorFilter on shapes on the SDL leg - grayscale
   ColorMatrix, tint (SrcIn), and lighting - which used to be inert. Top row is
   a red->green->blue gradient with no filter (reference) then grayscaled. */
private fun runFilterTest() {
    nativeComposeWindow(
        title = "filtertest",
        width = 440,
        height = 300,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 12) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("filtertest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("filtertest: wrote filtertest.bmp (${vW}x${vH})")
                } else println("filtertest: FAIL (no snapshot)")
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(Color(0xFF202020))) {
                fun off(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
                fun sz(w: Float, h: Float) = androidx.compose.ui.geometry.Size(w, h)
                val vGrad = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Red, Color.Green, Color.Blue), startX = 20f, endX = 420f,
                )
                val vGray = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })
                // Reference gradient, then the same gradient desaturated to grayscale.
                drawRect(vGrad, topLeft = off(20f, 20f), size = sz(400f, 50f))
                drawRect(vGrad, topLeft = off(20f, 80f), size = sz(400f, 50f), colorFilter = vGray)
                // Solid red tinted blue (SrcIn) -> blue block.
                drawRect(Color.Red, topLeft = off(20f, 150f), size = sz(120f, 60f),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Blue))
                // Solid red grayscaled -> gray block (luminance of red).
                drawRect(Color.Red, topLeft = off(160f, 150f), size = sz(120f, 60f), colorFilter = vGray)
                // Gray brightened via lighting (add).
                drawRect(Color(0xFF808080), topLeft = off(300f, 150f), size = sz(120f, 60f),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.lighting(Color.White, Color(0x40404040)))
            }
        }
    }
}

/* --jointest: three thick chevron polylines stroked with Miter / Bevel / Round
   joins, plus a Butt vs Square capped line. On the SDL leg corners used to notch
   and Square fell back to Butt; now joins fill the corner and Square projects. */
private fun runJoinTest() {
    nativeComposeWindow(
        title = "jointest",
        width = 460,
        height = 320,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 12) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("jointest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("jointest: wrote jointest.bmp (${vW}x${vH})")
                } else println("jointest: FAIL (no snapshot)")
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(Color(0xFF202020))) {
                fun off(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
                fun chevron(x: Float): androidx.compose.ui.graphics.Path =
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, 40f); lineTo(x + 70f, 110f); lineTo(x, 180f)
                    }
                val vJoins = listOf(
                    androidx.compose.ui.graphics.StrokeJoin.Miter to Color.Cyan,
                    androidx.compose.ui.graphics.StrokeJoin.Bevel to Color.Yellow,
                    androidx.compose.ui.graphics.StrokeJoin.Round to Color.Magenta,
                )
                vJoins.forEachIndexed { vI, (vJoin, vColor) ->
                    drawPath(chevron(30f + vI * 140f), vColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 26f, join = vJoin))
                }
                // Butt (top) vs Square (bottom) capped lines - Square extends past the endpoints.
                drawLine(Color.White, off(40f, 240f), off(200f, 240f), strokeWidth = 24f, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
                drawLine(Color.Green, off(40f, 290f), off(200f, 290f), strokeWidth = 24f, cap = androidx.compose.ui.graphics.StrokeCap.Square)
            }
        }
    }
}

/* --rotimgtest: a two-colour image (top red / bottom blue) rotated 30 via
   graphicsLayer. On the SDL leg images used to stay axis-aligned (SDL_RenderTexture);
   now the rotated layer produces a tilted image via a textured SDL_RenderGeometry quad. */
private fun runRotImgTest() {
    nativeComposeWindow(
        title = "rotimgtest",
        width = 300,
        height = 300,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 12) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("rotimgtest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("rotimgtest: wrote rotimgtest.bmp (${vW}x${vH})")
                }
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF303030)),
                contentAlignment = Alignment.Center,
            ) {
                val vImg = twoColorBmp().decodeToImageBitmap()
                androidx.compose.foundation.Image(
                    bitmap = vImg,
                    contentDescription = null,
                    modifier = Modifier.size(150.dp, 90.dp).graphicsLayer(rotationZ = 30f),
                )
            }
        }
    }
}

// A 16x12 BMP: top half red, bottom half blue (bottom-up rows) — asymmetric so
// rotation is visible.
private fun twoColorBmp(): ByteArray {
    fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )
    val vW = 16; val vH = 12
    val vBlue = byteArrayOf(0xFF.toByte(), 0, 0); val vRed = byteArrayOf(0, 0, 0xFF.toByte())
    var vPixels = ByteArray(0)
    for (vRow in 0 until vH) {                 // BMP is bottom-up → low rows are the bottom
        val vColor = if (vRow < vH / 2) vBlue else vRed
        repeat(vW) { vPixels += vColor }
    }
    val vFileHeader = byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) + le32(54 + vPixels.size) + le32(0) + le32(54)
    val vDib = le32(40) + le32(vW) + le32(vH) + le16(1) + le16(24) + le32(0) + le32(vPixels.size) +
        le32(2835) + le32(2835) + le32(0) + le32(0)
    return vFileHeader + vDib + vPixels
}

// A minimal 2x2 24bpp red BMP (no compression) — valid input for SDL3_image.
private fun tinyRedBmp(): ByteArray {
    fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )
    val vPixel = byteArrayOf(0, 0, 0xFF.toByte())          // BGR red
    val vRow = vPixel + vPixel + byteArrayOf(0, 0)          // 2px + 2 pad = 8 bytes (4-byte aligned)
    val vPixels = vRow + vRow                               // 2 rows = 16 bytes
    val vFileHeader = byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) + le32(70) + le32(0) + le32(54)
    val vDib = le32(40) + le32(2) + le32(2) + le16(1) + le16(24) + le32(0) + le32(16) +
        le32(2835) + le32(2835) + le32(0) + le32(0)
    return vFileHeader + vDib + vPixels
}

private fun runNav3Test() {
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

/* Frame-by-frame trace of AnimatedVisibility(fadeIn+expandVertically / fadeOut+
   shrinkVertically): logs the AV container's animated size and the window-Y of a
   marker Box below it every frame, toggling visibility three times. Diagnoses
   (a) a size snap between just-before-end and end of the animation and (b)
   instant (non-animated) transitions on subsequent toggles.

   Expected healthy trace: ~24 smooth frames per toggle ending exactly at 0/60,
   PLUS one 16px marker jump when the fully-shrunk node unmounts (exit end) or
   mounts (enter start) — that's the parent's spacedBy(16) collapsing, inherent
   upstream behaviour (spacing applies to zero-height children too), NOT a bug. */
private fun runAnimVisTest() {
    val vShown = mutableStateOf(true)
    var vAvSize = androidx.compose.ui.unit.IntSize(-1, -1)
    var vMarkerY = -1f
    var vLastLog = ""
    nativeComposeWindow(
        title = "animvistest",
        width = 600,
        height = 500,
        onFrame = { _, vFrame ->
            // Log only when something moved — keeps the trace readable.
            val vLine = "size=$vAvSize markerY=$vMarkerY shown=${vShown.value}"
            if (vLine != vLastLog) {
                println("animvistest: f=$vFrame $vLine")
                vLastLog = vLine
            }
            when (vFrame) {
                60 -> { println("animvistest: === HIDE 1 ==="); vShown.value = false; true }
                140 -> { println("animvistest: === SHOW 2 ==="); vShown.value = true; true }
                220 -> { println("animvistest: === HIDE 3 ==="); vShown.value = false; true }
                300 -> false
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            // spacedBy mirrors the demo's Section column — the end-of-exit jump
            // reported on the FoundationExtra screen involves the spacing around
            // the AnimatedVisibility node collapsing when the node unmounts.
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(30.dp).background(androidx.compose.ui.graphics.Color(0xFFC07040)))
                androidx.compose.animation.AnimatedVisibility(
                    visible = vShown.value,
                    modifier = Modifier.onSizeChanged { vAvSize = it },
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFF7040C0)),
                    ) {
                        Text("Animated content", modifier = Modifier.padding(14.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF40C070))
                        .onGloballyPositioned { vMarkerY = it.positionInRoot().y },
                )
            }
        }
    }
}

/* Boots a window whose full-size surface opens an m3 AlertDialog on click (the real
   material3 → ui.window.Dialog path, default DialogProperties ⇒ animateTransition on),
   injects the click, then dumps screenshots during the 0.2s appearance animation and
   after it settles; then injects Escape and dumps mid-disappearance (0.1s reverse,
   via the popup host's exit deferral) + after. Visual check: mid shots must show the
   dialog semi-transparent, slightly scaled-down and shifted down vs the settled shot,
   and exit_end must show no dialog at all — skiko-parity animation both ways. */
private fun runDialogAnimTest() {
    fun snap(inBridge: com.compose.sdl.RenderBackend, inName: String) {
        val vSnap = inBridge.snapshotBgra() ?: return
        val (vW, vH, vBgra) = vSnap
        writeFile(inName, encodeBmpBgra32(vW, vH, vBgra))
        println("dialoganimtest: wrote $inName")
    }
    nativeComposeWindow(
        title = "dialoganimtest",
        width = 1000,
        height = 700,
        onFrame = { vBridge, vFrame ->
            when (vFrame) {
                30 -> { com.compose.sdl.injectMouseEvent(1, 500f, 350f); true }
                32 -> { com.compose.sdl.injectMouseEvent(2, 500f, 350f); true }
                36 -> { snap(vBridge, "dialog_mid1.bmp"); true }
                40 -> { snap(vBridge, "dialog_mid2.bmp"); true }
                90 -> { snap(vBridge, "dialog_end.bmp"); true }
                92 -> {
                    com.compose.sdl.injectKey(41, true)   // Escape → dismiss
                    com.compose.sdl.injectKey(41, false)
                    true
                }
                97 -> { snap(vBridge, "dialog_exit_mid.bmp"); true }
                140 -> { snap(vBridge, "dialog_exit_end.bmp"); false }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            var vShow by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { vShow = true },
            )
            if (vShow) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { vShow = false },
                    title = { Text("Animated dialog") },
                    text = { Text("Appearance animation parity with the JVM (skiko) Dialog.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { vShow = false }) { Text("OK") }
                    },
                )
            }
        }
    }
}

/* Boots a real window (installs the SDL TextMeasurer), then builds an upstream Paragraph via the
   vendored factory (→ SdlParagraph) for a long string constrained to a narrow width. Verifies it
   wrapped to multiple lines, has positive size, and that getHorizontalPosition/getOffsetForPosition
   round-trip — proving the paragraph-engine measurement bridge works. */
private fun runParagraphTest() {
    nativeComposeWindow(
        title = "paragraphtest",
        width = 400,
        height = 200,
        onFrame = { _, frameIndex ->
            if (frameIndex == 10) {
                val vP = androidx.compose.ui.text.Paragraph(
                    text = "Hello world foo bar baz qux quux corge grault garply waldo",
                    style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = 120),
                    density = androidx.compose.ui.unit.Density(1f),
                    fontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                )
                val vHpos = vP.getHorizontalPosition(3, true)
                val vOffBack = vP.getOffsetForPosition(androidx.compose.ui.geometry.Offset(vHpos, 2f))
                val vLine = vP.getLineForOffset(30)
                println("paragraphtest: lineCount=${vP.lineCount} w=${vP.width} h=${vP.height} hpos(3)=$vHpos offBack=$vOffBack lineFor(30)=$vLine")
                val vPass = vP.lineCount >= 2 && vP.height > 0f && vP.width > 0f && vOffBack in 2..4
                println(if (vPass) "paragraphtest: PASS (real width-wrapped Paragraph via SdlParagraph)" else "paragraphtest: FAIL")
                false
            } else true
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {}
    }
}

/* Prints paragraph metrics (single-line cell, lineHeight-styled single + triple line,
   first baseline) for a font-size sweep at density 1 — the native half of the
   metrics-alignment probe. Mirror of MainJvm's `--metrics`; both must print the same
   numbers for the parity text drift to vanish. */
private fun runMetricsProbe() {
    fun paragraph(inText: String, inSize: Int, inLineHeight: Int?, inM3Style: Boolean): androidx.compose.ui.text.Paragraph =
        androidx.compose.ui.text.Paragraph(
            text = inText,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = inSize.sp,
                lineHeight = inLineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                lineHeightStyle = if (inM3Style) {
                    androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                    )
                } else null,
            ),
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = 10_000),
            density = androidx.compose.ui.unit.Density(1f),
            fontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(),
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    nativeComposeWindow(
        title = "metricsprobe",
        width = 300,
        height = 200,
        onFrame = { _, frameIndex ->
            if (frameIndex == 10) {
                for (vSize in listOf(11, 12, 14, 16, 22, 24)) {
                    val vLh = vSize + 6
                    val vCell = paragraph("Hg", vSize, null, false)
                    val vOne = paragraph("Hg", vSize, vLh, false)
                    val vThree = paragraph("Hg\nHg\nHg", vSize, vLh, false)
                    val vOneM3 = paragraph("Hg", vSize, vLh, true)
                    val vThreeM3 = paragraph("Hg\nHg\nHg", vSize, vLh, true)
                    println(
                        "metrics: size=$vSize lh=$vLh cell=${vCell.height} " +
                            "one=${vOne.height} three=${vThree.height} " +
                            "base1=${vOne.firstBaseline} base3=${vThree.lastBaseline} " +
                            "oneM3=${vOneM3.height} threeM3=${vThreeM3.height} base1M3=${vOneM3.firstBaseline}"
                    )
                }
                for ((vS, vL) in listOf(24 to 24, 24 to 25, 32 to 24, 16 to 16)) {
                    val vB = paragraph("Hg", vS, vL, true)
                    println("metrics: boundary $vS/$vL m3=${vB.height} base=${vB.firstBaseline}")
                }
                val vBig = paragraph("42", 48, 24, false)
                val vBigM3 = paragraph("42", 48, 24, true)
                println("metrics: big48/lh24 raw=${vBig.height} base=${vBig.firstBaseline} m3=${vBigM3.height} baseM3=${vBigM3.firstBaseline}")
                println("metricsprobe: DONE")
                false
            } else true
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {}
    }
}

// ==================
// MARK: clicktest
// ==================

/* Boots a real nativeComposeWindow with a full-size clickable box, then injects
   move→press→release SDL mouse events a few frames apart (giving the upstream
   clickable's gesture coroutine time to launch + await between frames), and prints
   PASS/FAIL based on whether onClick fired. Proves the whole vendored interaction
   pipeline works under the real Sdl3MainDispatcher + frame loop. */
private fun runClickTest() {
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
                        else "clicktest: FAIL (0 clicks — upstream clickable did not fire)"
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

/* Same injection harness as clicktest, but the target is a Material Switch (Modifier.toggleable
   from the vendored foundation.selection). Asserts onCheckedChange flipped the state — proving
   toggleable rides the same verified upstream interaction path as clickable. */
private fun runToggleTest() {
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

/* Boots a window with a real BasicTextField, clicks it to focus (focus-on-click via the
   FocusOwner), then injects TEXT_INPUT ("A","B") and a Backspace key through the live SDL path.
   Asserts the field edits to "A" — proving click-to-focus + typing + editing keys route to the
   focused field via ComposeRootHost.dispatchKeyEvent + the synthesised typed-key path. */
private fun runKeyTest() {
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

/* Boots a tall Column wrapped in the vendored Modifier.verticalScroll, injects several wheel-down
   events through the live SDL path (→ processor → MouseWheelScrollingLogic), and asserts the
   ScrollState offset advanced — proving upstream scrolling works end-to-end. */
private fun runScrollTest() {
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
                    Text("Scroll row $i — lorem ipsum dolor sit", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

