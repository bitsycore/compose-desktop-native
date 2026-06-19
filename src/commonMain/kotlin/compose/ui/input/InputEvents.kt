package compose.ui.input

// ==================
// MARK: Input Events
// ==================

data class PointerEvent(
    val x: Int,
    val y: Int,
    val type: PointerEventType,
    val button: PointerButton = PointerButton.Primary
)

enum class PointerEventType { Press, Release, Move }
enum class PointerButton { Primary, Secondary, Middle }

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
