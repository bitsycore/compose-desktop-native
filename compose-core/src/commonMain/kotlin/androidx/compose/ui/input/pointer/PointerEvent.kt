package androidx.compose.ui.input.pointer

// ==================
// MARK: Pointer Events
// ==================

data class PointerEvent(
    val x: Int,
    val y: Int,
    val type: PointerEventType,
    val button: PointerButton = PointerButton.Primary
)

enum class PointerEventType { Press, Release, Move }
enum class PointerButton { Primary, Secondary, Middle }
