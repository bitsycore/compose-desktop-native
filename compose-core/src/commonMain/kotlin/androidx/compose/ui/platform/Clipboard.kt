package androidx.compose.ui.platform

// ==================
// MARK: Clipboard
// ==================

/* Platform clipboard. The native backend installs an SDL3-backed impl
   during composeWindow startup; commonMain holds a no-op default so
   tests / unset configurations don't crash on read. */
interface Clipboard {
    fun getText(): String?
    fun setText(inText: String)
}

private object NoOpClipboard : Clipboard {
    override fun getText(): String? = null
    override fun setText(inText: String) {}
}

var currentClipboard: Clipboard = NoOpClipboard
