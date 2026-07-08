import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import demo.shell.App

// The JVM comparison app's entry point. Runs the SAME shared App() shell + Core /
// Material 3 screens (expressive included) as :demo, but on Compose Desktop (JVM)
// against upstream org.jetbrains.compose. Compare side-by-side with
// `:demo:runDebugExecutable<host>`.
fun main() = application {
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
