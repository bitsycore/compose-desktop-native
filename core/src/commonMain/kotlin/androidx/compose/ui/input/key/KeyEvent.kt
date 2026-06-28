package androidx.compose.ui.input.key

import com.compose.desktop.native.input.KeyModifiers

// ==================
// MARK: Key Events
// ==================

// Project-only reduced shape — FIDELITY-flagged runtime-critical for the
// value-class + extension-props redesign upstream uses. `modifiers` reads
// the relocated KeyModifiers in com.compose.desktop.native.input.
class KeyEvent(
    val keyCode: Int,
    val char: Char?,
    val type: KeyEventType,
    val modifiers: KeyModifiers = KeyModifiers(),
) {
    override fun equals(other: Any?): Boolean = other is KeyEvent &&
        other.keyCode == keyCode && other.char == char &&
        other.type == type && other.modifiers == modifiers
    override fun hashCode(): Int {
        var h = keyCode
        h = 31 * h + (char?.hashCode() ?: 0)
        h = 31 * h + type.hashCode()
        h = 31 * h + modifiers.hashCode()
        return h
    }
    override fun toString(): String =
        "KeyEvent(keyCode=$keyCode, char=$char, type=$type, modifiers=$modifiers)"
}

enum class KeyEventType { Down, Up }
