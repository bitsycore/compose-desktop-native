package apidemo

import com.compose.sdl.AppWindowIcon
import com.compose.sdl.disableInfiniteAnimations
import com.compose.sdl.nativeComposeWindow
import com.compose.sdl.useVirtualFrameTime
import com.compose.sdl.windowHasInvalidations
import kotlinx.cinterop.ExperimentalForeignApi
import utils.encodeBmpBgra32
import utils.writeFile

// ==================
// MARK: Entry point (native - SDL window shell)
// ==================

// The voltic window/taskbar icon - pre-decoded .rgba blobs bundled into data.kres
// under icon/ by the bridge plugin (compose.desktop.native { icon {} }); the
// backend uses the largest as the base and the rest as alternate sizes.
private val kAppIcon = AppWindowIcon(
    light = listOf("icon/voltic-icon-128.rgba", "icon/voltic-icon-32.rgba"),
    dark = listOf("icon/voltic-icon-dark-128.rgba", "icon/voltic-icon-dark-32.rgba")
)

private const val kWidth = 1240
private const val kHeight = 820

/** Safety net for content that never stops invalidating. */
private const val kMaxFrames = 300

/**
 * Entry point.
 *
 * `--screenshot=<path.bmp>` renders to quiescence and dumps the framebuffer, then
 * exits - the native half of the JVM-vs-native drift check (the JVM half is
 * `./gradlew :apidemo:run --args=--screenshot=<path.png>`, which goes through
 * ImageComposeScene). The capture is a GPU READBACK, not a screen grab, so it
 * does not care whether the window is visible or occluded.
 */
@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    val screenshot = args.firstOrNull { it.startsWith("--screenshot=") }?.substringAfter('=')
    if (screenshot != null) {
        // Match the JVM leg: freeze infinite animations and drive a virtual clock so
        // entrance animations settle deterministically instead of run-to-run.
        disableInfiniteAnimations = true
        useVirtualFrameTime = true
    }

    nativeComposeWindow(
        title = "API Manager",
        width = kWidth,
        height = kHeight,
        icon = kAppIcon,
        onFrame = if (screenshot != null) {
            // Capture once the window reports no pending invalidations for a few
            // consecutive frames, or at the cap. Mirrors :demo's parity path.
            var quietFrames = 0
            { backend, frameIndex ->
                quietFrames = if (windowHasInvalidations()) 0 else quietFrames + 1
                if (quietFrames >= 3 || frameIndex >= kMaxFrames) {
                    if (frameIndex >= kMaxFrames) {
                        println("Screenshot: quiescence not reached by frame $frameIndex - capturing anyway")
                    }
                    val snap = backend.snapshotBgra()
                    if (snap != null) {
                        val (w, h, bgra) = snap
                        writeFile(screenshot, encodeBmpBgra32(w, h, bgra))
                        println("Wrote screenshot: $screenshot (${w}x$h, settled at frame $frameIndex)")
                    } else {
                        println("Screenshot snapshot was null")
                    }
                    false   // quit
                } else true
            }
        } else null,
    ) { App() }
}
