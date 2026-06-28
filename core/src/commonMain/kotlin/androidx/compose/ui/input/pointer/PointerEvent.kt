package androidx.compose.ui.input.pointer

// ==================
// MARK: Pointer Events
// ==================

// Project-only reduced shape — FIDELITY-flagged runtime-critical for the
// upstream value-class redesign. Plain class with manual equals/hashCode
// drops component*/copy from the public surface in the meantime.
class PointerEvent(
    val x: Int,
    val y: Int,
    val type: PointerEventType,
    val button: PointerButton = PointerButton.Primary,
) {
    override fun equals(other: Any?): Boolean = other is PointerEvent &&
        other.x == x && other.y == y && other.type == type && other.button == button
    override fun hashCode(): Int {
        var h = x; h = 31 * h + y; h = 31 * h + type.hashCode(); h = 31 * h + button.hashCode(); return h
    }
    override fun toString(): String =
        "PointerEvent(x=$x, y=$y, type=$type, button=$button)"
}

enum class PointerEventType { Press, Release, Move }
enum class PointerButton { Primary, Secondary, Middle }
