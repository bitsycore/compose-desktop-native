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
data class TextFieldValue(
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
}
