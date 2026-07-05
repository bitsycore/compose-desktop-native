package com.compose.desktop.native.modifier

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// ==================
// MARK: InteractionSource convenience
// ==================

/* Convenience around the official MutableInteractionSource() factory. Official
   Compose uses `remember { MutableInteractionSource() }` directly; this helper
   is a project shorthand. Lives in :foundation because it references
   androidx.compose.foundation.interaction.MutableInteractionSource. */
@Composable
fun rememberMutableInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }
