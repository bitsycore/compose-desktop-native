package apidemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.ClipEntry
import com.compose.sdl.LocalComposeNativeWindow
import com.compose.sdl.TextLayoutConfig
import com.compose.sdl.registerMemoryResource
import com.compose.sdl.removeMemoryResource
import com.compose.sdl.res.ResourceKind
import com.compose.sdl.text.currentTextMeasurer

// ==================
// MARK: Native actuals — pure delegation to the port's SDL-backed APIs
// ==================

actual fun appDataDir(inOrg: String, inApp: String): String? =
    com.compose.sdl.appDataDir(inOrg, inApp)

actual fun revealInFileManager(inPath: String, inOnResult: ((Boolean) -> Unit)?) =
    com.compose.sdl.revealInFileManager(inPath, inOnResult)

actual fun fileManagerName(): String = com.compose.sdl.fileManagerName()

actual fun showSaveFileDialog(inDefaultName: String?, inOnResult: (String?) -> Unit) =
    com.compose.sdl.showSaveFileDialog(inDefaultName, inOnResult)

actual fun showOpenFileDialog(inOnResult: (String?) -> Unit) =
    com.compose.sdl.showOpenFileDialog(inOnResult)

actual fun clipEntryOfText(inText: String): ClipEntry = ClipEntry.withPlainText(inText)

actual fun registerMemoryImage(inKey: String, inBytes: ByteArray) = registerMemoryResource(inKey, inBytes)

actual fun removeMemoryImage(inKey: String) = removeMemoryResource(inKey)

@Composable
actual fun memoryImagePainter(inKey: String, inSvg: Boolean): Painter =
    com.compose.sdl.res.painterResource(inKey, if (inSvg) ResourceKind.Svg else ResourceKind.Raster)

actual fun wrappedRowCount(inText: String, inFontPx: Int, inMaxWidthPx: Int, inFamilyName: String?): Int =
    currentTextMeasurer.wrap(inText, inFontPx, inMaxWidthPx, inFamilyName).lines.size

actual var editorTabWidth: Int
    get() = TextLayoutConfig.tabWidth
    set(value) {
        TextLayoutConfig.tabWidth = value
    }

internal actual val systemFileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

actual fun createApiHttpClient(): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(io.ktor.client.engine.curl.Curl)

@Composable
actual fun InstallWindowHooks(inOnCloseRequest: () -> Boolean, inOnKeyShortcut: (KeyEvent) -> Boolean) {
    val vWindow = LocalComposeNativeWindow.current
    DisposableEffect(inOnCloseRequest, inOnKeyShortcut) {
        vWindow.setOnCloseRequest(inOnCloseRequest)
        vWindow.setOnKeyShortcut(inOnKeyShortcut)
        onDispose { vWindow.setOnCloseRequest(null); vWindow.setOnKeyShortcut(null) }
    }
}
