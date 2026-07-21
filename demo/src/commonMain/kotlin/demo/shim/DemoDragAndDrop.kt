package demo.shim

import androidx.compose.ui.draganddrop.DragAndDropEvent

// ==================
// MARK: demoReadFilePaths / demoReadText — DragAndDropEvent payload shim
// ==================

/** Upstream's DragAndDropTransferData is an `expect class` with no members
 * declared in commonMain — payload access is per-platform (this native port
 * exposes filePaths / text; JVM would read the AWT Transferable). Small
 * expect/actual pair so ClipboardScreen-style common demos can surface the
 * dropped content without dragging a whole platform abstraction in. */

expect fun DragAndDropEvent.demoReadFilePaths(): List<String>
expect fun DragAndDropEvent.demoReadText(): String?
