package androidx.compose.ui.text.style

// ==================
// MARK: TextAlign
// ==================

/* Horizontal alignment of text inside its laid-out box. Useful with a
   width-defining modifier (fillMaxWidth, width(...)) — without one, the
   layout box is just the heuristic glyph width and centering is a no-op. */
enum class TextAlign {
    Start,
    Center,
    End,
}
