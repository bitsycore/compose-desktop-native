package androidx.compose.ui.focus

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

// ==================
// MARK: Focusability — native actual
// ==================

/* Desktop/SDL behaviour: non-text components ARE focusable by default (matches the
   upstream desktop actual). Mobile returns false; we're desktop-first. */
internal actual fun systemDefinedCanFocus(node: CompositionLocalConsumerModifierNode): Boolean = true
