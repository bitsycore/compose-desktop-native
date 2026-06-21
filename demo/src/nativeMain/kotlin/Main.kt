import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.Res
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import com.compose.desktop.native.GpuMode
import com.compose.desktop.native.LocalComposeNativeWindow
import com.compose.desktop.native.composeWindow
import composeresources.generated.compose_logo
import composeresources.generated.heart
import composeresources.generated.notice
import composeresources.generated.photo
import composeresources.generated.star

// ==================
// MARK: CLI args
// ==================

/* Parsed view of the demo's command line.

   --gpu=auto | none | skia.metal | skia.opengl |
         sdl3 | sdl3.auto | sdl3.software |
         sdl3.opengl | sdl3.metal | sdl3.vulkan |
         sdl3.d3d11 | sdl3.d3d12                 (default: auto)
   --screen=Buttons | TextField | ...            (default: full app w/ sidebar)
   --screenshot=path.bmp                         capture after frames and quit
   --width=W  --height=H                         (default 1000 / 700)
   --frames=N                                    screenshot delay in frames

   Names match the Screen registry entries (case-insensitive). */
private data class CliArgs(
    val gpu: GpuMode = GpuMode.Auto,
    val screen: String? = null,
    val screenshot: String? = null,
    val width: Int = 1000,
    val height: Int = 700,
    val frames: Int = 6,
)

/* Translates the --gpu= string to the right GpuMode sealed instance.
   Accepts both dotted (`skia.metal`) and dashed (`skia-metal`) forms,
   plus the bare driver names (`metal`, `opengl`, …) as Skia aliases
   for backwards compatibility. */
private fun parseGpu(inValue: String): GpuMode = when (inValue.lowercase().replace('-', '.')) {
    "auto"           -> GpuMode.Auto
    "none", "cpu"    -> GpuMode.None
    "metal", "skia.metal"   -> GpuMode.Skia.Metal
    "opengl", "gl", "skia.opengl" -> GpuMode.Skia.OpenGL
    "sdl3", "sdl3.auto"     -> GpuMode.Sdl3.Auto
    "sdl3.software", "sdl3.sw" -> GpuMode.Sdl3.Software
    "sdl3.opengl"    -> GpuMode.Sdl3.OpenGL
    "sdl3.metal"     -> GpuMode.Sdl3.Metal
    "sdl3.vulkan"    -> GpuMode.Sdl3.Vulkan
    "sdl3.d3d11"     -> GpuMode.Sdl3.D3D11
    "sdl3.d3d12"     -> GpuMode.Sdl3.D3D12
    else -> {
        println("Unknown --gpu=$inValue, using auto")
        GpuMode.Auto
    }
}

private fun parseArgs(argv: Array<String>): CliArgs {
    var vArgs = CliArgs()
    for (arg in argv) {
        val eq = arg.indexOf('=')
        if (!arg.startsWith("--") || eq < 0) continue
        val key = arg.substring(2, eq)
        val value = arg.substring(eq + 1)
        vArgs = when (key) {
            "gpu"        -> vArgs.copy(gpu = parseGpu(value))
            "screen"     -> vArgs.copy(screen = value)
            "screenshot" -> vArgs.copy(screenshot = value)
            "width"      -> vArgs.copy(width = value.toIntOrNull() ?: vArgs.width)
            "height"     -> vArgs.copy(height = value.toIntOrNull() ?: vArgs.height)
            "frames"     -> vArgs.copy(frames = value.toIntOrNull() ?: vArgs.frames)
            else -> vArgs
        }
    }
    return vArgs
}

fun main(args: Array<String>) {
    val vCli = parseArgs(args)
    val vTitle = buildString {
        append("ComposeNativeSDL3 Showcase")
        if (vCli.screen != null) append(" — ").append(vCli.screen)
        append(" [").append(vCli.gpu).append("]")
    }

    composeWindow(
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

@OptIn(ExperimentalForeignApi::class)
private fun writeFile(inPath: String, inBytes: ByteArray) {
    val vFile = fopen(inPath, "wb") ?: run {
        println("fopen failed for $inPath")
        return
    }
    fwrite(inBytes.refTo(0), 1u, inBytes.size.toULong(), vFile)
    fclose(vFile)
}

/* Minimal BMP writer: 24-bit BGR (drop alpha) with the classic 40-byte
   BITMAPINFOHEADER. Universally readable, including `sips`. Rows are
   bottom-up (positive height) and padded to 4 bytes. */
private fun encodeBmpBgra32(inWidth: Int, inHeight: Int, inBgra: ByteArray): ByteArray {
    val kFileHeader = 14
    val kInfoHeader = 40
    val vRowBytes = inWidth * 3
    val vRowPad = (4 - vRowBytes % 4) % 4
    val vStride = vRowBytes + vRowPad
    val vPixelBytes = vStride * inHeight
    val vTotal = kFileHeader + kInfoHeader + vPixelBytes
    val vOut = ByteArray(vTotal)

    fun putU16LE(off: Int, v: Int) {
        vOut[off]     = (v and 0xFF).toByte()
        vOut[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }
    fun putU32LE(off: Int, v: Int) {
        vOut[off]     = (v and 0xFF).toByte()
        vOut[off + 1] = ((v ushr 8) and 0xFF).toByte()
        vOut[off + 2] = ((v ushr 16) and 0xFF).toByte()
        vOut[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    // BITMAPFILEHEADER
    vOut[0] = 'B'.code.toByte(); vOut[1] = 'M'.code.toByte()
    putU32LE(2, vTotal)
    putU32LE(10, kFileHeader + kInfoHeader)

    // BITMAPINFOHEADER
    val info = kFileHeader
    putU32LE(info + 0,  kInfoHeader)
    putU32LE(info + 4,  inWidth)
    putU32LE(info + 8,  inHeight)              // positive → bottom-up
    putU16LE(info + 12, 1)
    putU16LE(info + 14, 24)
    putU32LE(info + 16, 0)                     // BI_RGB
    putU32LE(info + 20, vPixelBytes)
    putU32LE(info + 24, 2835)
    putU32LE(info + 28, 2835)
    putU32LE(info + 32, 0)
    putU32LE(info + 36, 0)

    // Source is top-down BGRA; convert to bottom-up 24-bit BGR.
    val vPixelOffset = kFileHeader + kInfoHeader
    for (y in 0 until inHeight) {
        val vSrcRow = (inHeight - 1 - y) * inWidth * 4
        val vDstRow = vPixelOffset + y * vStride
        for (x in 0 until inWidth) {
            vOut[vDstRow + x * 3 + 0] = inBgra[vSrcRow + x * 4 + 0]  // B
            vOut[vDstRow + x * 3 + 1] = inBgra[vSrcRow + x * 4 + 1]  // G
            vOut[vDstRow + x * 3 + 2] = inBgra[vSrcRow + x * 4 + 2]  // R
        }
    }
    return vOut
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
    Screen("State / Remember") { StateScreen() },
    Screen("Interaction")    { InteractionScreen() },
    Screen("Recomposition")  { RecompositionScreen() },
    Screen("Colors")         { ColorsScreen() },
    Screen("Scroll")         { ScrollScreen() },
    Screen("LazyColumn")     { LazyColumnScreen() },
    Screen("Counter")        { CounterScreen() },
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

@Composable
private fun ScreenTitle(title: String, subtitle: String? = null) {
    Column {
        Text(text = title, color = MaterialTheme.colors.onBackground, fontSize = 30.sp)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/* Card-wrapped section with a small caption above the demonstrated content.
   Every screen uses this for a consistent visual grid. */
@Composable
private fun Section(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = MaterialTheme.colors.onSurface, fontSize = 15.sp)
            if (description != null) {
                Text(
                    description,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }
            content()
        }
    }
}

// ==================
// MARK: Screens
// ==================

@Composable
private fun WindowScreen() {
    val window = LocalComposeNativeWindow.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Window",
            "ComposeNativeWindow handle — read live state, retitle, resize, minimize, fullscreen, close.",
        )

        Section("Live state", "Refreshes automatically — every property is snapshot-backed") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Title:      ${window.title}", fontSize = 13.sp)
                Text("Size:       ${window.width} × ${window.height} pt  (${window.pixelWidth} × ${window.pixelHeight} px)", fontSize = 13.sp)
                Text("DPR:        ${window.pixelDensity}", fontSize = 13.sp)
                Text("Minimized:  ${window.isMinimized}", fontSize = 13.sp)
                Text("Maximized:  ${window.isMaximized}", fontSize = 13.sp)
                Text("Fullscreen: ${window.isFullscreen}", fontSize = 13.sp)
            }
        }

        Section("Renderer", "What the active RenderBackend ended up using") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Requested:  ${window.gpuMode}", fontSize = 13.sp)
                Text("Active:     ${window.rendererName}", fontSize = 13.sp)
            }
        }

        Section("Title", "Write to SDL_SetWindowTitle live") {
            var draft by remember { mutableStateOf(window.title) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.width(300.dp),
                    singleLine = true,
                )
                Button(onClick = { window.setTitle(draft) }) {
                    Text("Set title", color = MaterialTheme.colors.onPrimary)
                }
            }
        }

        Section("Resize", "SDL_SetWindowSize — both axes in logical points") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { window.setSize(800, 600) }) {
                    Text("800 × 600", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = { window.setSize(1000, 700) }) {
                    Text("1000 × 700", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = { window.setSize(1280, 800) }) {
                    Text("1280 × 800", color = MaterialTheme.colors.onPrimary)
                }
            }
        }

        Section("Window state", "Minimize / maximize / restore / fullscreen") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { window.minimize() }) {
                    Text("Minimize", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = { window.maximize() }) {
                    Text("Maximize", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = { window.restore() }) {
                    Text("Restore", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = { window.toggleFullscreen() }) {
                    Text(
                        if (window.isFullscreen) "Exit fullscreen" else "Fullscreen",
                        color = MaterialTheme.colors.onPrimary,
                    )
                }
            }
        }

        Section("Close", "window.close() = same as the OS close button") {
            Button(
                onClick = { window.close() },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error,
                    contentColor = MaterialTheme.colors.onError,
                ),
            ) {
                Text("Close window", color = MaterialTheme.colors.onError)
            }
        }
    }
}

@Composable
private fun ButtonsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Buttons",
            "Filled / Outlined / Text variants with shape, content padding, and enabled states.",
        )

        Section("Filled Button", "Default: RoundedCornerShape(4.dp), Material primary container") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Action", color = MaterialTheme.colors.onPrimary) }
                Button(onClick = {}, enabled = false) {
                    Text("Disabled", color = MaterialTheme.colors.onPrimary)
                }
            }
        }

        Section("OutlinedButton", "Transparent fill, 1.dp border, primary content colour") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}) {
                    Text("Outlined", color = MaterialTheme.colors.primary)
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Disabled", color = MaterialTheme.colors.primary)
                }
            }
        }

        Section("TextButton", "No background, no border — text-only affordance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {}) {
                    Text("Text", color = MaterialTheme.colors.primary)
                }
            }
        }

        Section("Shape variants", "shape = RoundedCornerShape(0/12.dp) and CircleShape with contentPadding 0") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}, shape = RoundedCornerShape(0.dp)) {
                    Text("Rect", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                    Text("12dp", color = MaterialTheme.colors.onPrimary)
                }
                Button(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colors.onPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextFieldScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "TextField",
            "BasicTextField + Material wrappers. Click to focus, type, drag to select, ⌘C/⌘V/⌘Z.",
        )

        var single by remember { mutableStateOf("") }
        var outlined by remember { mutableStateOf("") }
        var withError by remember { mutableStateOf("abc") }
        var multi by remember { mutableStateOf("Hello\nMulti-line text\nReturn to add a line\nUp / Down to navigate") }
        var raw by remember { mutableStateOf("BasicTextField (no chrome)") }

        Section("Material TextField (filled)", "label, placeholder, supportingText all wired") {
            TextField(
                value = single,
                onValueChange = { single = it },
                label = "Name",
                placeholder = "Type your name…",
                supportingText = "Click, drag-select, ⌘C / ⌘V / ⌘Z",
                modifier = Modifier.width(320.dp),
            )
        }

        Section("OutlinedTextField") {
            OutlinedTextField(
                value = outlined,
                onValueChange = { outlined = it },
                label = "Email",
                placeholder = "user@example.com",
                modifier = Modifier.width(320.dp),
            )
        }

        Section("Error state", "isError = true turns border, label, cursor, supporting text red") {
            TextField(
                value = withError,
                onValueChange = { withError = it },
                label = "Password",
                isError = true,
                supportingText = "Too short",
                modifier = Modifier.width(320.dp),
            )
        }

        Section("Multi-line", "Return inserts \\n, Up/Down navigates rows, field grows to fit") {
            OutlinedTextField(
                value = multi,
                onValueChange = { multi = it },
                label = "Bio",
                modifier = Modifier.width(420.dp),
            )
        }

        var soft by remember { mutableStateOf(
            "This text auto-wraps at the field width. Resize the field by changing its " +
            "Modifier.width and the wrap recalculates. Click anywhere to position the " +
            "cursor; Up / Down move between wrapped lines while preserving the preferred " +
            "x-column. Selection rectangles span all wrapped rows."
        ) }
        Section("Soft-wrap", "Long text wraps at word boundaries — field grows vertically to fit") {
            OutlinedTextField(
                value = soft,
                onValueChange = { soft = it },
                label = "Long form",
                modifier = Modifier.width(320.dp),
            )
        }

        var oneLine by remember { mutableStateOf("singleLine = true; Return does nothing, no wrap") }
        Section("singleLine = true", "Return is suppressed; wrap is disabled — text overflows past the field width") {
            TextField(
                value = oneLine,
                onValueChange = { oneLine = it },
                label = "Title",
                singleLine = true,
                modifier = Modifier.width(280.dp),
            )
        }

        Section("Raw BasicTextField", "No chrome — bare cursor + text") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.surface,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.15f)),
                modifier = Modifier.width(320.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BasicTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        color = MaterialTheme.colors.onSurface,
                        cursorColor = MaterialTheme.colors.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Text", "fontSize / color / textAlign / softWrap")

        Section("Font sizes", "All Sp-typed, scale with theme") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default text", color = MaterialTheme.colors.onSurface)
                Text("fontSize 12.sp", fontSize = 12.sp, color = MaterialTheme.colors.onSurface)
                Text("fontSize 24.sp", fontSize = 24.sp, color = MaterialTheme.colors.onSurface)
                Text("fontSize 32.sp", fontSize = 32.sp, color = MaterialTheme.colors.onSurface)
            }
        }

        Section("Colors") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Color.Red", color = Color.Red, fontSize = 16.sp)
                Text("MaterialTheme.colors.primary", color = MaterialTheme.colors.primary, fontSize = 16.sp)
                Text("MaterialTheme.colors.secondary", color = MaterialTheme.colors.secondary, fontSize = 16.sp)
            }
        }

        Section("TextAlign over fillMaxWidth()") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(400.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                        color = MaterialTheme.colors.onBackground)
                    Text("Center", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground)
                    Text("End", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        color = MaterialTheme.colors.onBackground)
                }
            }
        }

        Section("Soft-wrap", "Long text auto-wraps at word boundaries inside a constrained width") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump!",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Section("softWrap = false", "Overflows horizontally — Surface clips, content cropped at the right edge") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "This long sentence does not wrap and will get clipped.",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Layout", "Row / Column / Box — Arrangement and Alignment")

        Section("Row with spacedBy(16.dp)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Swatch("A"); Swatch("B"); Swatch("C")
            }
        }

        Section("Row with Arrangement.SpaceBetween", "Children pinned to start and end, evenly spaced") {
            Row(
                modifier = Modifier.width(400.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Swatch("L"); Swatch("M"); Swatch("R")
            }
        }

        Section("Column with spacedBy(8.dp) + CenterHorizontally") {
            Column(
                modifier = Modifier.width(120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Swatch("1"); Swatch("2"); Swatch("3")
            }
        }

        Section("Box with contentAlignment.Center") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.size(120.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Swatch("•") }
            }
        }
    }
}

@Composable
private fun ModifiersScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Modifiers", "background, border, clip, padding, size, offset, defaultMinSize, fillMaxWidth, color alpha")

        Section("background + border + padding") {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
            ) {
                Text("Content", color = MaterialTheme.colors.onPrimary)
            }
        }

        Section("clip(RoundedCornerShape(12.dp))", "Clips children only — the background here already follows the shape") {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 60.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("clipped", color = MaterialTheme.colors.onPrimary)
            }
        }

        Section("offset(x = 20.dp, y = 10.dp)", "Visual nudge only — doesn't change measured size or sibling layout") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("A")
                Box(modifier = Modifier.offset(x = 20.dp, y = 10.dp)) { Swatch("B↘") }
                Swatch("C")
            }
        }

        Section("defaultMinSize vs size", "defaultMinSize only kicks in when the incoming min is 0") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 120.dp, minHeight = 40.dp)
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("min 120 × 40", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("40", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }
            }
        }

        Section("padding overloads", "symmetric / per-axis / per-side — padding insets the content") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                        .padding(16.dp),
                ) { Text("padding(16.dp)", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 28.dp, vertical = 6.dp),
                ) { Text("padding(horizontal = 28, vertical = 6)", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                        .padding(start = 28.dp, top = 4.dp, end = 4.dp, bottom = 16.dp),
                ) { Text("padding(start = 28, top = 4, end = 4, bottom = 16)", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }
            }
        }

        Section("fillMaxWidth()", "Stretches to the parent's width (here, the Card's content width)") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("fillMaxWidth()", color = MaterialTheme.colors.onPrimary, fontSize = 13.sp) }
        }

        Section(
            "Opacity via Color alpha",
            "There's no Modifier.alpha() — fade by lowering the colour's alpha channel (Color.copy(alpha = …)). " +
                "The label keeps full alpha, showing it's per-colour, not a node-wide fade.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                for (vA in listOf(1f, 0.6f, 0.3f)) {
                    Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 40.dp)
                            .background(MaterialTheme.colors.primary.copy(alpha = vA), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("α $vA", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }
                }
            }
        }

        Section(
            "Modifier.alpha — node-wide opacity",
            "Fades the whole subtree as one layer (background, border, AND text together) — contrast with the per-colour fade above, where only the fill faded.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (vA in listOf(1f, 0.6f, 0.3f)) {
                    Box(
                        modifier = Modifier
                            .alpha(vA)
                            .size(width = 96.dp, height = 56.dp)
                            .background(MaterialTheme.colors.primary, RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("alpha $vA", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun ShapesScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Shapes", "RectangleShape, RoundedCornerShape(Dp / percent), CircleShape")

        Section("RectangleShape") {
            Box(modifier = Modifier.size(120.dp, 40.dp).background(MaterialTheme.colors.primary))
        }

        Section("RoundedCornerShape — radius 4 / 12 / 24 dp") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp)))
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(24.dp)))
            }
        }

        Section("CircleShape") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colors.primary, CircleShape))
                Box(modifier = Modifier.size(60.dp).background(MaterialTheme.colors.primary, CircleShape))
                Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colors.primary, CircleShape))
            }
        }

        Section("Borders on each shape", "Modifier.border respects the same Shape param as background") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, MaterialTheme.colors.primary))
                Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, MaterialTheme.colors.primary, RoundedCornerShape(12.dp)))
                Box(modifier = Modifier.size(48.dp).border(2.dp, MaterialTheme.colors.primary, CircleShape))
            }
        }
    }
}

@Composable
private fun CounterScreen() {
    var counter by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Counter", "The original +/- demo")
        Section("Click +/- to change the count") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Counter: $counter",
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 32.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { counter-- },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "-",
                            color = MaterialTheme.colors.onPrimary,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = { counter = 0 }) {
                        Text("Reset", color = MaterialTheme.colors.primary)
                    }
                    Button(
                        onClick = { counter++ },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "+",
                            color = MaterialTheme.colors.onPrimary,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// ==================
// MARK: State / Remember screen
// ==================

@Composable
private fun StateScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("State / Remember", "mutableStateOf, derived state, remember(key) re-init")

        Section("Basic counter", "var n by remember { mutableStateOf(0) }") {
            var n by remember { mutableStateOf(0) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { n++ }) { Text("Increment", color = MaterialTheme.colors.onPrimary) }
                Text("n = $n", color = MaterialTheme.colors.onSurface, fontSize = 16.sp)
            }
        }

        Section("List state", "remembered List<String>; reassign to add / drop") {
            var items by remember { mutableStateOf(listOf("alpha", "beta", "gamma")) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { items = items + "item ${items.size}" }) {
                        Text("Add", color = MaterialTheme.colors.onPrimary)
                    }
                    OutlinedButton(onClick = { if (items.isNotEmpty()) items = items.dropLast(1) }) {
                        Text("Remove", color = MaterialTheme.colors.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in items) Text("• $i", color = MaterialTheme.colors.onSurface, fontSize = 14.sp)
                }
            }
        }

        Section("Derived state", "Total recomputes on every recomposition that reads aText / bText") {
            var aText by remember { mutableStateOf("3") }
            var bText by remember { mutableStateOf("4") }
            val total = (aText.toIntOrNull() ?: 0) + (bText.toIntOrNull() ?: 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = aText, onValueChange = { aText = it }, modifier = Modifier.width(80.dp))
                Text("+", color = MaterialTheme.colors.onSurface, fontSize = 20.sp)
                OutlinedTextField(value = bText, onValueChange = { bText = it }, modifier = Modifier.width(80.dp))
                Text("=", color = MaterialTheme.colors.onSurface, fontSize = 20.sp)
                Text(total.toString(), color = MaterialTheme.colors.primary, fontSize = 20.sp)
            }
        }

        Section("remember(key)", "Toggling the key invalidates the memo so the lambda runs again") {
            var key by remember { mutableStateOf(0) }
            val rolledOnce = remember(key) { (0..99).random() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { key++ }) { Text("Re-roll", color = MaterialTheme.colors.onPrimary) }
                Text("rolled = $rolledOnce", color = MaterialTheme.colors.onSurface, fontSize = 16.sp)
            }
        }
    }
}

// ==================
// MARK: Interaction (hover / press / focus) screen
// ==================

@Composable
private fun InteractionScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Interaction", "Hover, press, focus state layers")

        Section("Hover overlay", "Move the cursor over each variant") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.onPrimary) }
                OutlinedButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
                TextButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
            }
        }

        Section("Press / drag-off", "Hold the mouse down; deeper overlay shows. Drag out and the press cancels.") {
            Button(onClick = {}) { Text("Press and hold", color = MaterialTheme.colors.onPrimary) }
        }

        Section("TextField focus", "Border color + width transition on focus / blur") {
            var a by remember { mutableStateOf("") }
            var b by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = a, onValueChange = { a = it }, label = "First", modifier = Modifier.width(160.dp))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = "Second", modifier = Modifier.width(160.dp))
            }
        }
    }
}

// ==================
// MARK: Recomposition diagnostics screen
// ==================

@Composable
private fun RecompositionScreen() {
    trackRecomposition("Recomposition/outer")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Recomposition",
            "RecompositionLogger taps in nested scopes; stdout shows [tag] recomposed #N",
        )

        Section(
            "Scope-narrowing",
            "Clicking + only invalidates the inner block — App, outer screen, sibling logs stay at #1",
        ) {
            var counter by remember { mutableStateOf(0) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InnerCounterBlock(counter) { counter = it }
                SiblingBlock()
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun trackRecomposition(tag: String) {
    val inst = remember(tag) { RecompositionTracker(tag) }
    SideEffect { inst.record() }
}

@Composable
private fun InnerCounterBlock(counter: Int, onChange: (Int) -> Unit) {
    trackRecomposition("Recomposition/inner")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onChange(counter + 1) }) { Text("+", color = MaterialTheme.colors.onPrimary) }
            Text("Counter: $counter", color = MaterialTheme.colors.onBackground, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SiblingBlock() {
    trackRecomposition("Recomposition/sibling")
    Text(
        "This block doesn't read the counter — its log stays at #1.",
        color = MaterialTheme.colors.onBackground,
        fontSize = 12.sp,
    )
}

// ==================
// MARK: Colors screen — Material palette swatches
// ==================

@Composable
private fun ColorsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Colors", "MaterialTheme.colors palette swatches")
        Section("Theme palette") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = MaterialTheme.colors
                ColorRow("primary",         c.primary,         c.onPrimary)
                ColorRow("primaryVariant",  c.primaryVariant,  c.onPrimary)
                ColorRow("secondary",       c.secondary,       c.onSecondary)
                ColorRow("background",      c.background,      c.onBackground)
                ColorRow("surface",         c.surface,         c.onSurface)
                ColorRow("error",           c.error,           c.onError)
            }
        }
    }
}

@Composable
private fun ColorRow(name: String, fill: Color, content: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 180.dp, height = 36.dp)
                .background(fill, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, color = content, fontSize = 14.sp)
        }
    }
}

// ==================
// MARK: Scroll screen
// ==================

@Composable
private fun ScrollScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Scroll",
            "Modifier.verticalScroll(rememberScrollState()), wheel events, auto-clip",
        )

        Section(
            "Fixed-height scrollable Box",
            "Hover over the box and use mouse wheel / trackpad. Content is 40 rows tall.",
        ) {
            val vScroll = rememberScrollState()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.background,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(400.dp).height(200.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 1..40) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colors.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$i",
                                        color = MaterialTheme.colors.onPrimary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Text(
                                    "Row $i — scroll to see all 40 items",
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Section(
            "App-shell scrolling",
            "Shrink the window vertically — the sidebar AND the main content area both gain scrollbars.",
        ) {
            Text(
                "Both panes were wrapped in verticalScroll(rememberScrollState()) inside App().",
                color = MaterialTheme.colors.onSurface,
                fontSize = 13.sp,
            )
        }
    }
}

// ==================
// MARK: LazyColumn screen
// ==================

@Composable
private fun LazyColumnScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("LazyColumn", "DSL: items / item / itemsIndexed inside a verticalScroll viewport")

        Section(
            "items(count) — 100 rows",
            "Mouse-wheel over the list area to scroll. Header item pinned at the top of the list (it scrolls with content — sticky headers TBD).",
        ) {
            val state = rememberLazyListState()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.background,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(420.dp).height(260.dp),
            ) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Text(
                            "LazyColumn — first item (a header)",
                            color = MaterialTheme.colors.primary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(100) { i ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colors.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$i",
                                    color = MaterialTheme.colors.onPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                "Item $i — generated via LazyColumn.items(100) { i -> … }",
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        Section(
            "itemsIndexed(list)",
            "Use itemsIndexed when you need (index, element) — same API as upstream",
        ) {
            val names = remember { listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta") }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.background,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(320.dp).height(160.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(names) { idx, name ->
                        Text(
                            "$idx. $name",
                            color = MaterialTheme.colors.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ==================
// MARK: Images / Resources screen
// ==================

@Composable
private fun ImagesScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Images / Resources",
            "composeResources bundled next to the binary, loaded via generated Res.* accessors — " +
                "PNG, JPG, SVG, Android vector XML, and raw bytes.",
        )

        Section(
            "Formats",
            "Each loads from composeResources/drawable through the active renderer's decoder " +
                "(SDL3_image on Windows; Skia on macOS/Linux). SVG + Android XML are rasterised.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                LabeledImage("PNG · alpha", Res.drawable.compose_logo)
                LabeledImage("JPG", Res.drawable.photo)
                LabeledImage("SVG", Res.drawable.star)
                LabeledImage("Android XML", Res.drawable.heart)
            }
        }

        Section("ContentScale", "The same PNG inside a fixed 110 × 64 box (clipped)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScaledImage("Fit", ContentScale.Fit)
                ScaledImage("Crop", ContentScale.Crop)
                ScaledImage("FillBounds", ContentScale.FillBounds)
            }
        }

        Section("alpha", "Per-image opacity via the alpha parameter") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for (vA in listOf(1f, 0.6f, 0.3f)) {
                    Image(
                        painter = Res.drawable.star,
                        contentDescription = "star at alpha $vA",
                        modifier = Modifier.size(48.dp),
                        alpha = vA,
                    )
                }
            }
        }

        Section("Raw bytes", "Res.readBytes(Res.files.notice) — no decoding, just the file") {
            val vText = remember { Res.readBytes(Res.files.notice)?.decodeToString() ?: "(resource missing)" }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    vText,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun LabeledImage(label: String, painter: Painter) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colors.background, RoundedCornerShape(8.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painter,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun ScaledImage(label: String, scale: ContentScale) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 64.dp)
                .background(MaterialTheme.colors.background, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Image(
                painter = Res.drawable.compose_logo,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale,
            )
        }
        Text(label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

// ==================
// MARK: Helpers
// ==================

@Composable
private fun Swatch(label: String, color: Color = MaterialTheme.colors.primary) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colors.onPrimary, fontSize = 14.sp)
    }
}
