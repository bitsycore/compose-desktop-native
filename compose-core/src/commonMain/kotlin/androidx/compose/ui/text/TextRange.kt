package androidx.compose.ui.text

import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: TextRange
// ==================

/* Inclusive-start, exclusive-end character range. Used for the cursor
   (start == end → collapsed) and for selections. Either end can be the
   "anchor" or "head" — TextFieldValue distinguishes via start vs end. */
data class TextRange(val start: Int, val end: Int) {
    val collapsed: Boolean get() = start == end
    val length: Int get() = max(start, end) - min(start, end)
    val min: Int get() = min(start, end)
    val max: Int get() = max(start, end)
    val reversed: Boolean get() = end < start

    fun coerceIn(inMin: Int, inMax: Int): TextRange =
        TextRange(start.coerceIn(inMin, inMax), end.coerceIn(inMin, inMax))

    companion object {
        val Zero: TextRange = TextRange(0, 0)
    }
}

fun TextRange(index: Int): TextRange = TextRange(index, index)
