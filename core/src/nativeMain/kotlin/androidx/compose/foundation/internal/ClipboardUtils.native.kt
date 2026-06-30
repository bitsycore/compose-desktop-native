package androidx.compose.foundation.internal

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

// Native actuals for vendored commonMain `ClipboardUtils.kt`. Mirror the
// macOS / iOS / wasm actuals — all plain-text-only since our ClipEntry
// carries a single optional `plainText` field. The SDL3 clipboard
// (PlatformClipboard.native.kt) provides full read+write support on
// every host platform so `isReadSupported` / `isWriteSupported` are
// always true.

internal actual suspend fun ClipEntry.readText(): String? = getPlainText()

internal actual suspend fun ClipEntry.readAnnotatedString(): AnnotatedString? {
	val vText = getPlainText() ?: return null
	return AnnotatedString(vText)
}

internal actual fun AnnotatedString?.toClipEntry(): ClipEntry? {
	if (this == null) return null
	return ClipEntry.withPlainText(this.text)
}

internal actual fun ClipEntry?.hasText(): Boolean = this?.getPlainText() != null

internal actual fun Clipboard.isReadSupported(): Boolean = true
internal actual fun Clipboard.isWriteSupported(): Boolean = true
