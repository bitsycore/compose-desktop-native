package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.LocalComposeNativeWindow

// ==================
// MARK: Window screen
// ==================

/** Extra demo windows opened from this screen. The app composition in MainNative.kt
   declares one keyed Window() per id in this list — the list IS the windows'
   lifetime (multi-window, Compose Desktop style). Each window is keyed by its
   own id, so closing one removes exactly THAT id (a count would always drop the
   last-declared window, closing the wrong one). */
val ExtraWindows = mutableStateListOf<Int>()
private var fNextExtraWindowId = 1

fun openExtraWindow() {
    ExtraWindows.add(fNextExtraWindowId++)
}

@Composable
internal fun WindowScreen() {
    val window = LocalComposeNativeWindow.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Window",
            "ComposeNativeWindow handle — read live state, retitle, resize, minimize, fullscreen, close.",
        )

        Section("Multi-window", "nativeComposeApp hosts several Window()s — each click composes one more") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { openExtraWindow() }) { Text("Open extra window") }
                Text("open: ${ExtraWindows.size}", fontSize = 13.sp)
            }
        }

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
                    Text("Set title", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Section("Resize", "SDL_SetWindowSize — both axes in logical points") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { window.setSize(800, 600) }) {
                    Text("800 × 600", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { window.setSize(1000, 700) }) {
                    Text("1000 × 700", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { window.setSize(1280, 800) }) {
                    Text("1280 × 800", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Section("Window state", "Minimize / maximize / restore / fullscreen") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { window.minimize() }) {
                    Text("Minimize", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { window.maximize() }) {
                    Text("Maximize", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { window.restore() }) {
                    Text("Restore", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = { window.toggleFullscreen() }) {
                    Text(
                        if (window.isFullscreen) "Exit fullscreen" else "Fullscreen",
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
