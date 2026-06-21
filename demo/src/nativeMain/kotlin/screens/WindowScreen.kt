package screens

import ScreenTitle
import Section
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.compose.desktop.native.*

// ==================
// MARK: Window screen
// ==================

@Composable
internal fun WindowScreen() {
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
