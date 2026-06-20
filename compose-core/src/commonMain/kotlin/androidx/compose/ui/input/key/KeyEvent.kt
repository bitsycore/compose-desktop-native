package androidx.compose.ui.input.key

// ==================
// MARK: Key Events
// ==================

data class KeyEvent(
    val keyCode: Int,
    val char: Char?,
    val type: KeyEventType,
    val modifiers: KeyModifiers = KeyModifiers()
)

enum class KeyEventType { Down, Up }

data class KeyModifiers(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
)
