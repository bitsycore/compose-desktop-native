package demo.shim

import androidx.compose.ui.platform.ClipEntry

// ==================
// MARK: DemoClipboard — portable ClipEntry construction / reading
// ==================

/* Upstream Compose has no COMMON way to build or read a ClipEntry — every
   platform actual differs. This port mirrors upstream's macOS actual
   (ClipEntry.withPlainText / getPlainText / getImage), while upstream JVM
   desktop wraps an AWT Transferable and exposes nothing text-typed. Shared
   screens go through these four helpers; each stack maps them 1:1 onto its
   native shape. */

/** A ClipEntry carrying plain text, for Clipboard.setClipEntry. */
expect fun demoClipEntryOfText(text: String): ClipEntry

/** A ClipEntry carrying an image from already-PNG-encoded bytes. */
expect fun demoClipEntryOfPng(pngBytes: ByteArray): ClipEntry

/** The entry's plain text, or null when it carries none. */
expect fun ClipEntry.demoPlainText(): String?

/** The entry's image as PNG-encoded bytes, or null when it carries none. */
expect fun ClipEntry.demoPngImage(): ByteArray?
