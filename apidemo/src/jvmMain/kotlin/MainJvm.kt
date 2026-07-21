package apidemo

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.decodeToImageBitmap

// The JVM comparison app's entry point. Runs the SAME shared App() as the
// native :apidemo, but on Compose Desktop (JVM) against upstream
// org.jetbrains.compose. Client-certificate (mTLS) features are native-only
// (they drive the bundled libcurl); the jvm actuals report that instead.
fun main() = application {
    // Voltic window icon (mirrors the native window icon). Staged onto the
    // classpath at icon/ by jvmProcessResources.
    val vIcon = remember {
        BitmapPainter(useResource("icon/voltic-icon-256.png") { it.readAllBytes().decodeToImageBitmap() })
    }
    Window(
        // The shared App installs a persist-then-close hook (InstallWindowHooks).
        onCloseRequest = { if (jvmOnCloseRequest?.invoke() != false) exitApplication() },
        onPreviewKeyEvent = { jvmOnKeyShortcut?.invoke(it) ?: false },
        title = "API Manager — JVM (upstream Compose)",
        icon = vIcon,
        state = rememberWindowState(width = 1240.dp, height = 820.dp),
    ) {
        App()
    }
}
