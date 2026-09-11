package apidemo

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import apidemo.compat.jvmOnCloseRequest
import apidemo.compat.jvmOnKeyShortcut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

// The JVM comparison app's entry point. Runs the SAME shared App() as the
// native :apidemo, but on Compose Desktop (JVM) against upstream
// org.jetbrains.compose. Client-certificate (mTLS) features are native-only
// (they drive the bundled libcurl); the jvm actuals report that instead.
//
//   --screenshot=<path.png>   render headlessly to quiescence, write, exit
//
// The screenshot mode is the JVM half of the native-vs-JVM drift check; the
// native half is `apidemo.exe --screenshot=<path.bmp>`. Both render offscreen
// (ImageComposeScene here, a GPU readback there), so neither needs a visible
// window and the comparison is unaffected by whatever is on screen.
fun main(args: Array<String>) {
    val screenshot = args.firstOrNull { it.startsWith("--screenshot=") }?.substringAfter('=')
    if (screenshot != null) {
        screenshotApp(File(screenshot), kWidth, kHeight)
        return
    }
    runInteractive()
}

private const val kWidth = 1240
private const val kHeight = 820

private fun runInteractive() = application {
    // Voltic window icon (mirrors the native window icon). Staged onto the
    // classpath at icon/ by jvmProcessResources.
    val vIcon = remember {
        BitmapPainter(useResource("icon/voltic-icon-256.png") { it.readAllBytes().decodeToImageBitmap() })
    }
    Window(
        // The shared App installs a persist-then-close hook (InstallWindowHooks).
        onCloseRequest = { if (jvmOnCloseRequest?.invoke() != false) exitApplication() },
        onPreviewKeyEvent = { jvmOnKeyShortcut?.invoke(it) ?: false },
        title = "API Manager - JVM (upstream Compose)",
        icon = vIcon,
        state = rememberWindowState(width = kWidth.dp, height = kHeight.dp),
    ) {
        App()
    }
}

// ==================
// MARK: Headless screenshot (drift check vs the native leg)
// ==================

private const val kFrameNanos = 16_666_667L
private const val kMaxFrames = 300

/** Infinite animations would never let the scene go quiet; upstream's test policy
freezes them at their initial value (same trick :demo's parity leg uses). */
private object CancelInfiniteAnimations : InfiniteAnimationPolicy {
    override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
        throw CancellationException("infinite animations disabled for the drift screenshot")
}

/** Render [App] offscreen at density 1 - matching the native leg, which lays out in
physical pixels - stepping a virtual 60fps clock until the scene stops invalidating. */
@OptIn(ExperimentalComposeUiApi::class)
private fun screenshotApp(outFile: File, width: Int, height: Int) {
    outFile.parentFile?.mkdirs()
    val scene = ImageComposeScene(
        width, height, density = Density(1f),
        coroutineContext = Dispatchers.Unconfined + CancelInfiniteAnimations,
    ) {
        App()
    }
    try {
        var nanos = 0L
        var frames = 0
        var image = scene.render(nanos)
        while (scene.hasInvalidations() && frames < kMaxFrames) {
            nanos += kFrameNanos
            image = scene.render(nanos)
            frames++
        }
        val png = image.encodeToData(EncodedImageFormat.PNG)
        if (png == null) {
            println("jvm screenshot: PNG encode failed")
            return
        }
        outFile.writeBytes(png.bytes)
        println("jvm screenshot: ${outFile.path} (settled after $frames frame(s))")
    } finally {
        scene.close()
    }
}
