import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.nativeComposeWindow
import screens.ButtonsScreen
import screens.CanvasScreen
import screens.CustomLayoutScreen
import screens.GraphicsLayerScreen
import screens.DesktopWidgetsScreen
import screens.DialogsScreen
import screens.DispatchersScreen
import screens.IconsScreen
import screens.WidgetsScreen
import screens.ColorsScreen
import screens.CounterScreen
import screens.ImagesScreen
import screens.InteractionScreen
import screens.LayoutScreen
import screens.LazyColumnScreen
import screens.ModifiersScreen
import screens.RecompositionScreen
import screens.ScrollScreen
import screens.ShapesScreen
import screens.StateScreen
import screens.TextFieldScreen
import screens.TextScreen
import screens.WindowScreen
import utils.encodeBmpBgra32
import utils.parseArgs
import utils.writeFile

// ==================
// MARK: Entry point
// ==================

fun main(args: Array<String>) {
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
    Screen("Widgets")        { WidgetsScreen() },
    Screen("Desktop")        { DesktopWidgetsScreen() },
    Screen("Dialogs")        { DialogsScreen() },
    Screen("Icons")          { IconsScreen() },
    Screen("Dispatchers")    { DispatchersScreen() },
    Screen("Canvas")         { CanvasScreen() },
    Screen("GraphicsLayer")  { GraphicsLayerScreen() },
    Screen("CustomLayout")   { CustomLayoutScreen() },
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
