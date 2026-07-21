package apidemo

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.ClipEntry
import io.ktor.client.*
import okio.FileSystem

// ==================
// MARK: Platform seams (native = com.compose.sdl / SDL3, jvm = AWT + upstream desktop)
// ==================
// The port modules are native-only, so shared code can't call com.compose.sdl
// directly — every SDL-backed API the app uses goes through these expects.
// Signatures mirror the native originals so the native actuals are pure
// delegation (same pattern as :demo's demo.shim package).

/** Per-user app-data dir (created on demand, ends with a separator) or null. */
expect fun appDataDir(inOrg: String, inApp: String): String?

/** Open the OS file manager showing the given path. Fire-and-forget. */
expect fun revealInFileManager(inPath: String, inOnResult: ((Boolean) -> Unit)? = null)

/** "Explorer" / "Finder" / "Files" — for menu labels. */
expect fun fileManagerName(): String

/** Async save-file dialog; the callback gets the chosen absolute path or null. */
expect fun showSaveFileDialog(inDefaultName: String? = null, inOnResult: (String?) -> Unit)

/** Async open-file dialog; the callback gets the chosen absolute path or null. */
expect fun showOpenFileDialog(inOnResult: (String?) -> Unit)

/** A plain-text clipboard entry (ClipEntry construction differs per stack). */
expect fun clipEntryOfText(inText: String): ClipEntry

// ============
//  In-memory response images

/** Register/replace decoded-image source bytes under a key (resp-image://N). */
expect fun registerMemoryImage(inKey: String, inBytes: ByteArray)

/** Drop a registered image's bytes. */
expect fun removeMemoryImage(inKey: String)

/** Painter for a registered image key (raster or SVG). */
@Composable
expect fun memoryImagePainter(inKey: String, inSvg: Boolean): Painter

// ============
//  Text layout

/** Rows the given line occupies when soft-wrapped to inMaxWidthPx at
inFontPx (already density-scaled). Used by the body gutter so its line
numbers stay aligned with the wrapped body text. */
expect fun wrappedRowCount(inText: String, inFontPx: Int, inMaxWidthPx: Int, inFamilyName: String?): Int

/** Editor tab width in spaces — how wide a typed '\t' renders. On the native
stack this drives the project text pipeline (TextLayoutConfig); the jvm
parity app only stores the preference. */
expect var editorTabWidth: Int

// ============
//  Infrastructure

/** okio's FileSystem.SYSTEM is declared per-platform, not in its common
metadata — surface it through a seam. */
internal expect val systemFileSystem: FileSystem

/** The app's Ktor client: Curl on native (bundled libcurl — same TLS stack as
the mTLS path), CIO on the jvm parity target. */
expect fun createApiHttpClient(): HttpClient

/** Window-shell integration: an intercepting close handler (return true to
allow the close) and an app-wide key-shortcut handler; both cleared when
the composition leaves. */
@Composable
expect fun InstallWindowHooks(inOnCloseRequest: () -> Boolean, inOnKeyShortcut: (KeyEvent) -> Boolean)
