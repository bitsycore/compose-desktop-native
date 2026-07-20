package apidemo

import com.compose.sdl.AppWindowIcon
import com.compose.sdl.nativeComposeWindow

// ==================
// MARK: Entry point (native — SDL window shell)
// ==================

// The voltic window/taskbar icon — the background-less "mark". The blobs are the
// pre-decoded .rgba files bundled into data.kres under icon/ by the build
// (generateAppIconRgba); the backend uses the largest as the base and the rest
// as alternate sizes. The mark is theme-neutral, so light == dark (dark defaults
// to light here). The .exe keeps the full branded icon (see build.gradle.kts).
private val kAppIcon = AppWindowIcon(
    light = listOf("icon/voltic-mark-128.rgba", "icon/voltic-mark-32.rgba"),
)

fun main() {
    nativeComposeWindow(title = "API Manager", width = 1240, height = 820, icon = kAppIcon) { App() }
}
