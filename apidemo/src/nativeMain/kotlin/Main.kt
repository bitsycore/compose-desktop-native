package apidemo

import com.compose.sdl.AppWindowIcon
import com.compose.sdl.nativeComposeWindow

// ==================
// MARK: Entry point (native — SDL window shell)
// ==================

// The voltic window/taskbar icon, resolved against the OS theme at runtime. The
// blobs are the pre-decoded .rgba files bundled into data.kres under icon/ by
// the build (generateAppIconRgba). List the larger size first is irrelevant —
// the backend uses the largest as the base and the rest as alternate sizes.
private val kAppIcon = AppWindowIcon(
    light = listOf("icon/voltic-icon-128.rgba", "icon/voltic-icon-32.rgba"),
    dark = listOf("icon/voltic-icon-dark-128.rgba", "icon/voltic-icon-dark-32.rgba"),
)

fun main() {
    nativeComposeWindow(title = "API Manager", width = 1240, height = 820, icon = kAppIcon) { App() }
}
