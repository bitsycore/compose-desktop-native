import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.Window
import com.compose.sdl.nativeComposeApp
import com.compose.sdl.nativeComposeWindow
import demo.registry.allCategories
import demo.shell.App
import screens.*
import utils.encodeBmpBgra32
import utils.parseArgs
import utils.writeFile

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
    // Verifies B6b key/text routing: a focused node receives injected SDL TEXT_INPUT
    // (onTextInput) and KEY (onKeyEvent) events through the FocusOwner.
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

    val vCli = parseArgs(args)
    val vTitle = buildString {
        append("ComposeDesktopNative Showcase")
        if (vCli.screen != null) append(" — ").append(vCli.screen)
        append(" [").append(vCli.gpu).append("]")
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
            { bridge, frameIndex ->
                if (frameIndex == vCli.frames) {
                    val vSnap = bridge.snapshotBgra()
                    if (vSnap != null) {
                        val (vW, vH, vBgra) = vSnap
                        val vBmp = encodeBmpBgra32(vW, vH, vBgra)
                        writeFile(vCli.screenshot, vBmp)
                        println("Wrote screenshot: ${vCli.screenshot} (${vW}x${vH})")
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

/* Boots a window with a real project BasicTextField, clicks it to focus (focus-on-click via the
   FocusOwner), then injects TEXT_INPUT ("A","B") and a Backspace key through the live SDL path.
   Asserts the field edits to "A" — proving click-to-focus + typing + editing keys route to the
   focused field via B6b (host.dispatchTextInput / dispatchKeyEvent). */
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
        // The exact regression path: a real project BasicTextField, focused by clicking it,
        // receiving typed text (SDL TEXT_INPUT) + editing keys (Backspace) via B6b.
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

