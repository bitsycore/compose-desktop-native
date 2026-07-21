@file:OptIn(ExperimentalComposeUiApi::class)

package demo.shim

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

/** JVM (Compose Desktop) actual: DragAndDropEvent exposes the payload through
 * the AWT flavor system via the awtTransferable extension (throws when the
 * event carries no transferable — hence the runCatching). */

actual fun DragAndDropEvent.demoReadFilePaths(): List<String> {
    val transferable = runCatching { awtTransferable }.getOrNull() ?: return emptyList()
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    @Suppress("UNCHECKED_CAST")
    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File> ?: return emptyList()
    return files.map { it.absolutePath }
}

actual fun DragAndDropEvent.demoReadText(): String? {
    val transferable = runCatching { awtTransferable }.getOrNull() ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    return runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
}
