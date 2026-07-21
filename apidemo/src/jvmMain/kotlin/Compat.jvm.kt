@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package apidemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.StringSelection
import java.io.File
import org.jetbrains.skia.Image

// ==================
// MARK: JVM actuals — AWT + upstream Compose Desktop
// ==================

private val kIsWindows = System.getProperty("os.name").startsWith("Windows")
private val kIsMac = System.getProperty("os.name").startsWith("Mac")

internal actual val systemFileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

actual fun createApiHttpClient(): io.ktor.client.HttpClient =
	io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO)

// ============
//  Window hooks — the shared App installs them; MainJvm wires them into the
//  upstream Window() (onCloseRequest / onPreviewKeyEvent).

internal var jvmOnCloseRequest: (() -> Boolean)? = null
internal var jvmOnKeyShortcut: ((KeyEvent) -> Boolean)? = null

@Composable
actual fun InstallWindowHooks(inOnCloseRequest: () -> Boolean, inOnKeyShortcut: (KeyEvent) -> Boolean) {
	androidx.compose.runtime.DisposableEffect(inOnCloseRequest, inOnKeyShortcut) {
		jvmOnCloseRequest = inOnCloseRequest
		jvmOnKeyShortcut = inOnKeyShortcut
		onDispose { jvmOnCloseRequest = null; jvmOnKeyShortcut = null }
	}
}

/** Mirrors SDL_GetPrefPath's layout: <os user-data dir>/<org>/<app>/ with a
   trailing separator, created on demand. */
actual fun appDataDir(inOrg: String, inApp: String): String? {
	val vHome = System.getProperty("user.home") ?: return null
	val vBase = when {
		kIsWindows -> System.getenv("APPDATA") ?: "$vHome/AppData/Roaming"
		kIsMac     -> "$vHome/Library/Application Support"
		else       -> System.getenv("XDG_DATA_HOME") ?: "$vHome/.local/share"
	}
	val vDir = File(File(vBase, inOrg), inApp)
	if (!vDir.exists() && !vDir.mkdirs()) return null
	return vDir.absolutePath + File.separator
}

/** Opens the path's containing folder (AWT has no cross-platform "select in
   file manager"); fire-and-forget off the EDT. */
actual fun revealInFileManager(inPath: String, inOnResult: ((Boolean) -> Unit)?) {
	Thread {
		val vOk = runCatching {
			val vFile = File(inPath)
			Desktop.getDesktop().open(if (vFile.isDirectory) vFile else vFile.parentFile ?: vFile)
		}.isSuccess
		inOnResult?.invoke(vOk)
	}.apply { isDaemon = true }.start()
}

actual fun fileManagerName(): String = when {
	kIsWindows -> "Explorer"
	kIsMac     -> "Finder"
	else       -> "Files"
}

/** AWT FileDialog runs its own nested event loop, so invoking from the EDT
   (where Compose Desktop also runs) keeps the UI serviced while modal. */
actual fun showSaveFileDialog(inDefaultName: String?, inOnResult: (String?) -> Unit) {
	EventQueue.invokeLater {
		val vDlg = FileDialog(null as Frame?, "Save", FileDialog.SAVE)
		inDefaultName?.let { vDlg.file = it }
		vDlg.isVisible = true
		inOnResult(vDlg.file?.let { vDlg.directory + it })
	}
}

actual fun showOpenFileDialog(inOnResult: (String?) -> Unit) {
	EventQueue.invokeLater {
		val vDlg = FileDialog(null as Frame?, "Open", FileDialog.LOAD)
		vDlg.isVisible = true
		inOnResult(vDlg.file?.let { vDlg.directory + it })
	}
}

/** Upstream desktop's ClipEntry wraps an AWT Transferable. */
actual fun clipEntryOfText(inText: String): ClipEntry = ClipEntry(StringSelection(inText))

// ============
//  In-memory response images

private val kMemoryImages = HashMap<String, ByteArray>()

actual fun registerMemoryImage(inKey: String, inBytes: ByteArray) { kMemoryImages[inKey] = inBytes }

actual fun removeMemoryImage(inKey: String) { kMemoryImages.remove(inKey) }

@Composable
actual fun memoryImagePainter(inKey: String, inSvg: Boolean): Painter {
	val vDensity = LocalDensity.current
	return remember(inKey, inSvg) {
		val vBytes = kMemoryImages[inKey]
		runCatching {
			if (vBytes == null) error("no bytes for $inKey")
			if (inSvg) loadSvgPainter(vBytes.inputStream(), vDensity)
			else BitmapPainter(Image.makeFromEncoded(vBytes).toComposeImageBitmap())
		}.getOrElse { BitmapPainter(ImageBitmap(1, 1)) }
	}
}

// ============
//  Text layout

/** Upstream TextMeasurer at density 1 so the px-valued font size maps 1:1
   (the caller passes an already density-scaled size). */
private val kRowMeasurer: TextMeasurer by lazy {
	TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
}

actual fun wrappedRowCount(inText: String, inFontPx: Int, inMaxWidthPx: Int, inFamilyName: String?): Int =
	runCatching {
		kRowMeasurer.measure(
			AnnotatedString(inText),
			TextStyle(fontSize = inFontPx.sp, fontFamily = monoFontFamily ?: FontFamily.Monospace),
			constraints = Constraints(maxWidth = inMaxWidthPx),
		).lineCount
	}.getOrDefault(1)

/** The jvm parity app only stores the preference — upstream's text pipeline has
   no global tab-width knob. Backed by state so menu checkmarks recompose. */
private var fTabWidth by mutableStateOf(4)
actual var editorTabWidth: Int
	get() = fTabWidth
	set(value) { fTabWidth = value }
