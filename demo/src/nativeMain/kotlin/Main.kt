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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import com.compose.desktop.native.GpuMode
import com.compose.desktop.native.composeWindow
import screens.ButtonsScreen
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
    "none", "cpu", "software"    -> GpuMode.Software
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
    Screen("Remember")       { StateScreen() },
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
internal fun ScreenTitle(title: String, subtitle: String? = null) {
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
internal fun Section(
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
// MARK: Helpers
// ==================

@Composable
internal fun Swatch(label: String, color: Color = MaterialTheme.colors.primary) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colors.onPrimary, fontSize = 14.sp)
    }
}
