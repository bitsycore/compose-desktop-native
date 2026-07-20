package apidemo

import com.compose.sdl.AppWindowIcon
import com.compose.sdl.nativeComposeWindow

// ==================
// MARK: Entry point (native — SDL window shell)
// ==================

// The voltic window/taskbar icon — pre-decoded .rgba blobs bundled into data.kres
// under icon/ by the bridge plugin (compose.desktop.native { icon {} }); the
// backend uses the largest as the base and the rest as alternate sizes.
private val kAppIcon = AppWindowIcon(
    light = listOf("icon/voltic-icon-128.rgba", "icon/voltic-icon-32.rgba"),
    dark = listOf("icon/voltic-icon-dark-128.rgba", "icon/voltic-icon-dark-32.rgba")
)

fun main() {
    nativeComposeWindow(title = "API Manager", width = 1240, height = 820, icon = kAppIcon) { App() }
}
