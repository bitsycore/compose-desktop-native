package com.compose.desktop.native.text

import androidx.compose.runtime.compositionLocalOf

// ==================
// MARK: LocalInSelectionContainer
// ==================

/* Non-official project CompositionLocal: true inside a SelectionContainer.
   Material Text reads it to decide whether to render via SelectableText.
   Official Compose has no such public local (it routes through an internal
   SelectionRegistrar), so this lives in the com.compose.desktop.native layer. */
val LocalInSelectionContainer = compositionLocalOf { false }
