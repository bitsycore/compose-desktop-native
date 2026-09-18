import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.compose.sdl.ComposeNativeWindow
import com.compose.sdl.nativeComposeWindow

/**
 `--winattrtest`: opens a window with the non-default Compose Desktop attributes
 set, then checks four things and exits -

   1. the attributes reach SDL and read back off the facade,
   2. the raw SDL escape hatch hands out the pointers the resolved renderer owns,
   3. onPreviewKeyEvent sees a key BEFORE the focused content does,
   4. `enabled = false` drops input entirely.
 */
fun runWindowAttrTest() {
    var vPreviewSaw = 0
    var vKeySaw = 0
    var vFacade: ComposeNativeWindow? = null
    var vFrames = 0
    nativeComposeWindow(
        title = "attr probe",
        width = 420,
        height = 240,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true,
        enabled = true,
        // Returning false lets the event continue down the chain, so this also
        // proves preview runs first without swallowing anything.
        onPreviewKeyEvent = { vPreviewSaw++; false },
        onKeyEvent = { vKeySaw++; false },
        onFrame = { _, _ ->
            vFrames++
            // Let the first frames settle so SDL has applied everything.
            if (vFrames < 3) return@nativeComposeWindow true
            if (vFrames == 3) {
                com.compose.sdl.injectKey(41, true)    // Escape down
                com.compose.sdl.injectKey(41, false)
                return@nativeComposeWindow true
            }
            if (vFrames == 4) {
                // Nothing is focused, so the content declines and BOTH window
                // handlers must have seen the key.
                println("KEYCHAIN preview=$vPreviewSaw onKey=$vKeySaw")
                // Now disable the window and inject again: neither may fire.
                vFacade?.setEnabled(false)
                com.compose.sdl.injectKey(41, true)
                com.compose.sdl.injectKey(41, false)
                return@nativeComposeWindow true
            }
            if (vFrames == 5) {
                println("DISABLED preview=$vPreviewSaw onKey=$vKeySaw (must be unchanged)")
                vFacade?.setEnabled(true)
            }
            val vW = vFacade
            if (vW == null) {
                println("ATTR FAIL: window facade never captured")
            } else {
                println(
                    "ATTR undecorated=${vW.isUndecorated} resizable=${vW.isResizable} " +
                        "alwaysOnTop=${vW.isAlwaysOnTop} focusable=${vW.isFocusable} " +
                        "enabled=${vW.isEnabled}"
                )
                val vRaw = vW.rawSdlHandles()
                println(
                    "RAW gpu=${vW.gpuMode} window=${vRaw.window != null} " +
                        "renderer=${vRaw.renderer != null} glContext=${vRaw.glContext != null} " +
                        "metalView=${vRaw.metalView != null}"
                )
            }
            false
        },
    ) {
        SideEffect { vFacade = window }
        Box(Modifier.fillMaxSize()) { Text("attr probe") }
    }
}
