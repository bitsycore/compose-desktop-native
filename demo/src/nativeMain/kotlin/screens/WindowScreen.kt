package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import com.compose.sdl.LocalComposeNativeWindow
import com.compose.sdl.LocalWindowState

// ==================
// MARK: Window screen
// ==================

/** Extra demo windows opened from this screen. The app composition in MainNative.kt
   declares one keyed Window() per id in this list - the list IS the windows'
   lifetime (multi-window, Compose Desktop style). Each window is keyed by its
   own id, so closing one removes exactly THAT id (a count would always drop the
   last-declared window, closing the wrong one). */
val ExtraWindows = mutableStateListOf<Int>()
private var mNextExtraWindowId = 1

fun openExtraWindow() {
    ExtraWindows.add(mNextExtraWindowId++)
}

@Composable
internal fun WindowScreen() {
    val window = LocalComposeNativeWindow.current
    val windowState = LocalWindowState.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Window",
            "ComposeNativeWindow handle - read live state, retitle, resize, placement.",
        )

        Section("Multi-window", "nativeComposeApp hosts several Window()s - each click composes one more") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { openExtraWindow() }) { Text("Open extra window") }
                Text("open: ${ExtraWindows.size}", fontSize = 13.sp)
            }
        }

        Section("Live state", "Refreshes automatically - every property is snapshot-backed") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Title:      ${window.title}", fontSize = 13.sp)
                Text("Size:       ${windowState.size}", fontSize = 13.sp)
                Text("Position:   ${windowState.position}", fontSize = 13.sp)
                Text("Placement:  ${windowState.placement}", fontSize = 13.sp)
                Text("Minimized:  ${windowState.isMinimized}", fontSize = 13.sp)
                Text("Visible:    ${window.isVisible}", fontSize = 13.sp)
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
                Button(onClick = { window.title = draft }) {
                    Text("Set title", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Section("Resize", "SDL_SetWindowSize - both axes via DpSize") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { windowState.size = DpSize(800.dp, 600.dp) }) {
                    Text("800 × 600", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { windowState.size = DpSize(1000.dp, 700.dp) }) {
                    Text("1000 × 700", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { windowState.size = DpSize(1280.dp, 800.dp) }) {
                    Text("1280 × 800", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Section("Window state", "Floating / Maximized / Fullscreen / Minimized") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { windowState.isMinimized = true }) {
                    Text("Minimize", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { windowState.placement = WindowPlacement.Maximized }) {
                    Text("Maximize", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { windowState.placement = WindowPlacement.Floating; windowState.isMinimized = false }) {
                    Text("Restore", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Fullscreen
                    }
                }) {
                    Text(
                        if (windowState.placement == WindowPlacement.Fullscreen) "Exit fullscreen" else "Fullscreen",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        Section("Close", "window.close() = same as the OS close button") {
            Button(
                onClick = { window.close() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Close window", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
