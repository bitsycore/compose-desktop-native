import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.compose.desktop.native.graphics.blend
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.desktop.native.Window
import com.compose.desktop.native.nativeComposeApp
import com.compose.desktop.native.nativeComposeWindow
import demo.DropdownMenu
import demo.DropdownMenuItem
import demo.menuAnchor
import demo.rememberMenuAnchor
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
                val vMatch = AllScreens.firstOrNull { it.name.equals(vCli.screen, ignoreCase = true) }
                if (vMatch == null) {
                    println("Unknown --screen='${vCli.screen}'. Available: ${AllScreens.joinToString { it.name }}")
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
    // Extra windows opened from WindowScreen — one Window() per count; closing
    // one (OS button or its Close button) decrements the state.
    repeat(ExtraWindowCount.value) { vIdx ->
        Window(
            onCloseRequest = { ExtraWindowCount.value-- },
            title = "Extra window ${vIdx + 1}",
            width = 460,
            height = 300,
            gpu = vCli.gpu,
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ExtraWindowContent(vIdx + 1)
            }
        }
    }
    }
}

/* Content of the demo's extra windows — each has its own composition, focus,
   input routing, and render loop; the counter proves per-window state. */
@Composable
private fun com.compose.desktop.native.ComposeWindowScope.ExtraWindowContent(inIndex: Int) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Extra window #$inIndex", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
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
                    com.compose.desktop.native.injectKey(41, true)   // SDL_SCANCODE_ESCAPE
                    com.compose.desktop.native.injectKey(41, false)
                    true
                }
                // Phase 2: click the text field to focus it, then Escape —
                // the user-facing SearchBar scenario (field focused while
                // the back handler should collapse the bar).
                30 -> { com.compose.desktop.native.injectMouseEvent(1, 200f, 100f); true }
                32 -> { com.compose.desktop.native.injectMouseEvent(2, 200f, 100f); true }
                44 -> {
                    com.compose.desktop.native.injectKey(41, true)
                    com.compose.desktop.native.injectKey(41, false)
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
    fun snap(inBridge: com.compose.desktop.native.RenderBackend, inName: String) {
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
                30 -> { com.compose.desktop.native.injectMouseEvent(1, 200f, 230f); true }
                32 -> { com.compose.desktop.native.injectMouseEvent(2, 200f, 230f); true }
                70 -> { snap(vBridge, "esc_before.bmp"); true }
                80 -> {
                    com.compose.desktop.native.injectKey(41, true)   // Escape
                    com.compose.desktop.native.injectKey(41, false)
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
                20 -> { com.compose.desktop.native.injectMouseEvent(0, 200f, 150f); true }
                26 -> { com.compose.desktop.native.injectMouseEvent(1, 200f, 150f); true }
                32 -> { com.compose.desktop.native.injectMouseEvent(2, 200f, 150f); true }
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
                20 -> { com.compose.desktop.native.injectMouseEvent(0, 200f, 150f); true }
                26 -> { com.compose.desktop.native.injectMouseEvent(1, 200f, 150f); true }
                32 -> { com.compose.desktop.native.injectMouseEvent(2, 200f, 150f); true }
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
                12 -> { com.compose.desktop.native.injectMouseEvent(1, 200f, 100f); true } // click field to focus
                14 -> { com.compose.desktop.native.injectMouseEvent(2, 200f, 100f); true }
                24 -> { com.compose.desktop.native.injectTextInput("A"); true }
                28 -> { com.compose.desktop.native.injectTextInput("B"); true }
                32 -> { com.compose.desktop.native.injectKey(42, true); com.compose.desktop.native.injectKey(42, false); true } // Backspace → "A"
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
                    com.compose.desktop.native.injectWheel(200f, 150f, 0f, -3f) // wheel down
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

// ==================
// MARK: Screen registry
// ==================

private data class Screen(val name: String, val content: @Composable () -> Unit)

/* The top-level buckets the sidebar dropdown switches between.
   - Foundation: base Compose Multiplatform ported onto SDL3 — androidx.compose
     .ui / .foundation / .animation / runtime. These prove the native engine
     (layout, draw, input, text, state, animation) works from the ground up.
   - Material3: the vendored external library (~99.9% copied verbatim from
     upstream). These prove that a real component library builds on top of the
     re-implemented ui/foundation layers unchanged.
   - Native: NOT androidx — the project's own SDL / platform / desktop layer
     (com.compose.desktop.native.*): the window handle, OS clipboard, coroutine
     dispatchers on the SDL main loop, and desktop-only composite widgets. */
private enum class Category(val label: String) {
    Foundation("Core"),
    Material3("Material 3"),
    Native("Native · Desktop"),
}

// Base Compose (ui / foundation / animation / runtime) — the native engine.
private val FoundationScreens: List<Screen> = listOf(
    Screen("Text")              { TextScreen() },
    Screen("AnnotatedString")   { AnnotatedStringScreen() },
    Screen("Layout")            { LayoutScreen() },
    Screen("CustomLayout")      { CustomLayoutScreen() },
    Screen("Modifiers")         { ModifiersScreen() },
    Screen("ModShortcuts")      { ModifierShortcutsScreen() },
    Screen("Shapes")            { ShapesScreen() },
    Screen("Path")              { PathScreen() },
    Screen("Canvas")            { CanvasScreen() },
    Screen("GraphicsLayer")     { GraphicsLayerScreen() },
    Screen("Shadows")           { ShadowScreen() },
    Screen("Colors")            { ColorsScreen() },
    Screen("Brushes")           { BrushScreen() },
    Screen("Images")            { ImagesScreen() },
    Screen("Scroll")            { ScrollScreen() },
    Screen("LazyColumn")        { LazyColumnScreen() },
    Screen("LazyGrid")          { LazyGridScreen() },
    Screen("LazyExtra")         { LazyExtraScreen() },
    Screen("Gestures")          { GestureScreen() },
    Screen("Interaction")       { InteractionScreen() },
    Screen("InteractionSource") { InteractionSourceScreen() },
    Screen("FocusRequester")    { FocusRequesterScreen() },
    Screen("Remember")          { StateScreen() },
    Screen("Counter")           { CounterScreen() },
    Screen("Recomposition")     { RecompositionScreen() },
    Screen("Animation")         { AnimationScreen() },
    Screen("Pager")             { PagerScreen() },
    Screen("GridsExtra")        { GridsExtraScreen() },
    Screen("FlowLayout")        { FlowLayoutScreen() },
    Screen("BasicText")         { FoundationTextScreen() },
    Screen("FoundationExtra")   { FoundationExtraScreen() },
)

// Vendored material3 component library on top of the native ui/foundation.
private val Material3Screens: List<Screen> = listOf(
    Screen("Buttons")           { ButtonsScreen() },
    Screen("Fab")               { FabScreen() },
    Screen("TextField")         { TextFieldScreen() },
    Screen("Widgets")           { WidgetsScreen() },
    Screen("Cards")             { CardsScreen() },
    Screen("Chips")             { ChipsScreen() },
    Screen("Navigation")        { NavigationScreen() },
    Screen("Lists")             { ListItemsScreen() },
    Screen("Icons")             { IconsScreen() },
    Screen("Dialogs")           { DialogsScreen() },
    Screen("AppBars")           { M3AppBarsScreen() },
    Screen("Drawers")           { M3DrawersScreen() },
    Screen("NavRails")          { M3RailsScreen() },
    Screen("FabExtra")          { M3FabExtraScreen() },
    Screen("ButtonsExtra")      { M3ButtonsExtraScreen() },
    Screen("Sheets")            { M3SheetsScreen() },
    Screen("Search")            { M3SearchScreen() },
    Screen("Tabs")              { M3TabsScreen() },
    Screen("Pickers")           { M3PickersScreen() },
    Screen("Carousel")          { M3CarouselScreen() },
    Screen("M3Misc")            { M3MiscScreen() },
)

// NOT androidx — the project's SDL / platform / desktop layer.
private val NativeScreens: List<Screen> = listOf(
    Screen("Window")            { WindowScreen() },
    Screen("Clipboard")         { ClipboardScreen() },
    Screen("Dispatchers")       { DispatchersScreen() },
    Screen("Desktop")           { DesktopWidgetsScreen() },
)

private fun screensFor(category: Category): List<Screen> = when (category) {
    Category.Foundation -> FoundationScreens
    Category.Material3 -> Material3Screens
    Category.Native -> NativeScreens
}

// Flat list for the `--screen=<name>` CLI lookup (names are unique across all).
private val AllScreens: List<Screen> = FoundationScreens + Material3Screens + NativeScreens

// ==================
// MARK: App shell — sidebar + content
// ==================

@Composable
fun App() {
    var category by remember { mutableStateOf(Category.Foundation) }
    var current by remember { mutableStateOf(FoundationScreens[0]) }
    val vScreens = screensFor(category)
    val vSidebarBg = MaterialTheme.colorScheme.surface.blend(MaterialTheme.colorScheme.onSurface, 0.02f)

    val vSidebarScroll = rememberScrollState()
    val vContentScroll = rememberScrollState()

    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        // Sidebar (vertically scrollable — try resizing the window short)
        Column(
            modifier = Modifier
                .width(190.dp)
                .fillMaxHeight()
                .background(vSidebarBg)
                .verticalScroll(vSidebarScroll)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ComposeNativeSDL3",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Dropdown: switch the whole nav list between Foundation and Material3.
            CategorySelector(
                category = category,
                onSelect = { picked ->
                    if (picked != category) {
                        category = picked
                        current = screensFor(picked).first()
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (screen in vScreens) {
                NavItem(
                    label = screen.name,
                    selected = screen.name == current.name,
                    onClick = { current = screen },
                )
            }
        }

        // Content (vertically scrollable — long screens fit a short window)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vContentScroll)
                .padding(24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            current.content()
        }
    }
}

/* The category dropdown at the top of the sidebar. A styled trigger row (current
   category + chevron) opens the project DropdownMenu anchored to it. */
@Composable
private fun CategorySelector(category: Category, onSelect: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val vAnchor = rememberMenuAnchor()
    val vTriggerBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(vAnchor)
                .background(vTriggerBg, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "CATEGORY",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                )
                Text(
                    text = category.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
            }
            MaterialSymbolsOutlined(
                if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                size = 20.dp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = vAnchor,
            offsetY = 4.dp,
            minWidth = 174.dp,
        ) {
            for (vCategory in Category.values()) {
                DropdownMenuItem(onClick = { onSelect(vCategory); expanded = false }) {
                    Text(
                        text = vCategory.label,
                        color = if (vCategory == category) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val hoveredSrc = remember { MutableInteractionSource() }
    val hovered by hoveredSrc.collectIsHoveredAsState()
    val vBg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        hovered  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else     -> Color.Transparent
    }
    val vFg = if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(vBg, RoundedCornerShape(8.dp))
            .hoverable(hoveredSrc)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = vFg, fontSize = 14.sp)
    }
}
