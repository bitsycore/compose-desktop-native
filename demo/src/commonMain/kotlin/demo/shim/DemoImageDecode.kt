package demo.shim

import androidx.compose.ui.graphics.ImageBitmap

// ==================
// MARK: demoDecodeImage — encoded-bytes → ImageBitmap shim
// ==================

/** Compose has no commonMain ByteArray → ImageBitmap decoder. Each platform
   provides one under a different name (Skia's Image.makeFromEncoded on JVM,
   the project's decodeEncodedImageBitmap on native), so the ClipboardScreen's
   "paste image" preview goes through this expect/actual. Returns null when
   the bytes aren't a decodable image. */
expect fun demoDecodeImage(bytes: ByteArray): ImageBitmap?
