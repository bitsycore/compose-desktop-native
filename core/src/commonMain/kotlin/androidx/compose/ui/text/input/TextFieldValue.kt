package androidx.compose.ui.text.input

import androidx.compose.ui.text.TextRange

// ==================
// MARK: TextFieldValue
// ==================

/* Compose's text-input state container. `text` is the current characters,
   `selection` is the cursor or selection range, `composition` is the IME's
   in-progress range (null when no IME composition is active). All three
   are passed through onValueChange callbacks so the caller can intercept
   edits before they're committed. */
class TextFieldValue(
    val text: String = "",
    val selection: TextRange = TextRange.Zero,
    val composition: TextRange? = null,
) {
    init {
        // Selection out of range would later index past the end of the text;
        // coerce defensively rather than failing at draw time.
        require(selection.start in 0..text.length && selection.end in 0..text.length) {
            "selection $selection out of bounds for text length ${text.length}"
        }
    }

    // Upstream's TextFieldValue is a plain class with explicit copy() overloads
    // — match that.
    fun copy(
        text: String = this.text,
        selection: TextRange = this.selection,
        composition: TextRange? = this.composition,
    ): TextFieldValue = TextFieldValue(text, selection, composition)

    override fun equals(other: Any?): Boolean = other is TextFieldValue &&
        other.text == text && other.selection == selection && other.composition == composition
    override fun hashCode(): Int {
        var h = text.hashCode()
        h = 31 * h + selection.hashCode()
        h = 31 * h + (composition?.hashCode() ?: 0)
        return h
    }
    override fun toString(): String =
        "TextFieldValue(text=$text, selection=$selection, composition=$composition)"
}
