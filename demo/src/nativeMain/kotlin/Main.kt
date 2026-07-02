import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.compose.desktop.native.graphics.blend
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.nativeComposeWindow
import screens.*
import utils.encodeBmpBgra32
import utils.parseArgs
import utils.writeFile

// ==================
// MARK: Entry point
// ==================

fun main(args: Array<String>) {
    // Phase 9 B4 probe: render a hand-built upstream LayoutNode tree through
    // ComposeOwner + Sdl3Canvas and screenshot it — proves the upstream pipeline
    // end-to-end without the full ComposeWindow pivot. `--pipetest=<path.bmp>`.
    val vPipe = args.firstOrNull { it.startsWith("--pipetest") }
    if (vPipe != null) {
        val vPath = if ("=" in vPipe) vPipe.substringAfter("=") else "pipetest.bmp"
        com.compose.desktop.native.renderer.sdl.runUpstreamPipelineProbe(vPath)
        return
    }

    val vCli = parseArgs(args)
    val vTitle = buildString {
        append("ComposeDesktopNative Showcase")
        if (vCli.screen != null) append(" — ").append(vCli.screen)
        append(" [").append(vCli.gpu).append("]")
    }

    nativeComposeWindow(
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
        MaterialTheme(colors = darkColors()) {
            if (vCli.screen != null) {
                val vMatch = Screens.firstOrNull { it.name.equals(vCli.screen, ignoreCase = true) }
                if (vMatch == null) {
                    println("Unknown --screen='${vCli.screen}'. Available: ${Screens.joinToString { it.name }}")
                    Text("Unknown screen: ${vCli.screen}", color = Color.Red, fontSize = 16.sp)
                } else {
                    // Single screen, no sidebar — wraps in the standard 24dp
                    // content padding plus background so visuals match the App.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background)
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
}

// ==================
// MARK: Screen registry
// ==================

private data class Screen(val name: String, val content: @Composable () -> Unit)

private val Screens: List<Screen> = listOf(
    Screen("Window")         { WindowScreen() },
    Screen("Buttons")        { ButtonsScreen() },
    Screen("TextField")      { TextFieldScreen() },
    Screen("Text")           { TextScreen() },
    Screen("Layout")         { LayoutScreen() },
    Screen("Modifiers")      { ModifiersScreen() },
    Screen("Shapes")         { ShapesScreen() },
    Screen("Images")         { ImagesScreen() },
    Screen("Remember")       { StateScreen() },
    Screen("Interaction")    { InteractionScreen() },
    Screen("Recomposition")  { RecompositionScreen() },
    Screen("Colors")         { ColorsScreen() },
    Screen("Scroll")         { ScrollScreen() },
    Screen("LazyColumn")     { LazyColumnScreen() },
    Screen("Counter")        { CounterScreen() },
    Screen("Clipboard")      { ClipboardScreen() },
    Screen("Widgets")        { WidgetsScreen() },
    Screen("Desktop")        { DesktopWidgetsScreen() },
    Screen("Dialogs")        { DialogsScreen() },
    Screen("Icons")          { IconsScreen() },
    Screen("Dispatchers")    { DispatchersScreen() },
    Screen("Canvas")         { CanvasScreen() },
    Screen("GraphicsLayer")  { GraphicsLayerScreen() },
    Screen("CustomLayout")   { CustomLayoutScreen() },
    Screen("Animation")      { AnimationScreen() },
    Screen("Gestures")       { GestureScreen() },
    Screen("Path")           { PathScreen() },
    Screen("ModShortcuts")   { ModifierShortcutsScreen() },
    Screen("LazyExtra")      { LazyExtraScreen() },
    Screen("InteractionSource") { InteractionSourceScreen() },
    Screen("FocusRequester") { FocusRequesterScreen() },
    Screen("AnnotatedString") { AnnotatedStringScreen() },
)

// ==================
// MARK: App shell — sidebar + content
// ==================

@Composable
fun App() {
    var current by remember { mutableStateOf(Screens[0]) }
    val vSidebarBg = MaterialTheme.colors.surface.blend(MaterialTheme.colors.onSurface, 0.02f)

    val vSidebarScroll = rememberScrollState()
    val vContentScroll = rememberScrollState()

    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
    ) {
        // Sidebar (vertically scrollable — try resizing the window short)
        Column(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
                .background(vSidebarBg)
                .verticalScroll(vSidebarScroll)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ComposeNativeSDL3",
                color = MaterialTheme.colors.primary,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (screen in Screens) {
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

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val vBg = when {
        selected -> MaterialTheme.colors.primary.copy(alpha = 0.20f)
        hovered  -> MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
        else     -> Color.Transparent
    }
    val vFg = if (selected) MaterialTheme.colors.primary
              else MaterialTheme.colors.onSurface.copy(alpha = 0.82f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(vBg, RoundedCornerShape(8.dp))
            .hoverable { hovered = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = vFg, fontSize = 14.sp)
    }
}
