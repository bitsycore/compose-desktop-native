import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import demo.registry.allCategories
import demo.shell.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

// The JVM comparison app: the SAME shared App() + screens as :demo, on upstream
// Compose Desktop. Interactive by default; a headless screenshot mode drives
// the parity harness (scripts/parity — compares each screen native vs jvm).
//
//   --screenshot-all=<dir>   render every registered screen to <dir>/<Name>.png
//   --width / --height       viewport size (default 1000 / 700)
//
// The single-screen wrapper MIRRORS MainNative's --screen path (dark theme,
// verticalScroll + 24dp padding) so layout constraints match the native
// screenshots pixel-for-pixel.
fun main(args: Array<String>) {
    val screenshotDir = args.firstOrNull { it.startsWith("--screenshot-all=") }?.substringAfter('=')
    if (screenshotDir != null) {
        screenshotAllScreens(
            outDir = File(screenshotDir),
            width = args.intArg("--width", 1000),
            height = args.intArg("--height", 700),
        )
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ComposeDesktopNative — JVM (upstream Compose)",
            state = rememberWindowState(width = 1000.dp, height = 700.dp),
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(isJvm = true)
            }
        }
    }
}

private fun Array<String>.intArg(name: String, default: Int): Int =
    firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.toIntOrNull() ?: default

/* Render each registered screen headlessly (density 1 to match the native
   physical-pixel screenshots) and write a PNG per screen. */
@OptIn(ExperimentalComposeUiApi::class)
private fun screenshotAllScreens(outDir: File, width: Int, height: Int) {
    outDir.mkdirs()
    val screens = allCategories().flatMap { it.screens }.distinctBy { it.name }
    for (screen in screens) {
        val scene = ImageComposeScene(width, height, density = Density(1f)) {
            ScreenHost { screen.content() }
        }
        try {
            val image = scene.render()
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: continue
            File(outDir, "${screen.name}.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
        println("jvm screenshot: ${screen.name}")
    }
}

/* Same wrapper as MainNative's --screen path. */
@Composable
private fun ScreenHost(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) { content() }
    }
}
