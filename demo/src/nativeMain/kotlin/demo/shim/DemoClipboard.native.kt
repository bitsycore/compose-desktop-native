package demo.shim

import androidx.compose.ui.platform.ClipEntry

/** Native actuals: 1:1 onto the port's ClipEntry, which mirrors upstream's
   macOS actual (withPlainText / getPlainText) extended with the PNG image
   path (withImage / getImage over SDL_[GS]etClipboardData "image/png"). */

actual fun demoClipEntryOfText(text: String): ClipEntry = ClipEntry.withPlainText(text)

actual fun demoClipEntryOfPng(pngBytes: ByteArray): ClipEntry = ClipEntry.withImage(pngBytes)

actual fun ClipEntry.demoPlainText(): String? = getPlainText()

actual fun ClipEntry.demoPngImage(): ByteArray? = getImage()
