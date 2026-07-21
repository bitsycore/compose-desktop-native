@file:OptIn(ExperimentalComposeUiApi::class)

package demo.shim

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** JVM (Compose Desktop) actuals: upstream's desktop ClipEntry wraps an AWT
   Transferable — text rides DataFlavor.stringFlavor, images ride
   DataFlavor.imageFlavor as a decoded java.awt.Image (PNG bytes are
   en/decoded with ImageIO at the edges). */

actual fun demoClipEntryOfText(text: String): ClipEntry = ClipEntry(StringSelection(text))

actual fun demoClipEntryOfPng(pngBytes: ByteArray): ClipEntry = ClipEntry(PngTransferable(pngBytes))

actual fun ClipEntry.demoPlainText(): String? {
    val transferable = asAwtTransferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    return runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
}

actual fun ClipEntry.demoPngImage(): ByteArray? {
    val transferable = asAwtTransferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
    val image = runCatching { transferable.getTransferData(DataFlavor.imageFlavor) as? java.awt.Image }
        .getOrNull() ?: return null
    val buffered = image as? BufferedImage ?: BufferedImage(
        image.getWidth(null).coerceAtLeast(1),
        image.getHeight(null).coerceAtLeast(1),
        BufferedImage.TYPE_INT_ARGB,
    ).also { it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() } }
    val out = ByteArrayOutputStream()
    val ok = runCatching { ImageIO.write(buffered, "png", out) }.getOrDefault(false)
    return if (ok) out.toByteArray() else null
}

/** Serves PNG bytes as DataFlavor.imageFlavor — AWT's canonical image exchange
   format, so pasting into other desktop apps works. */
private class PngTransferable(private val pngBytes: ByteArray) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any =
        if (flavor == DataFlavor.imageFlavor)
            ImageIO.read(ByteArrayInputStream(pngBytes)) ?: throw UnsupportedFlavorException(flavor)
        else throw UnsupportedFlavorException(flavor)
}
