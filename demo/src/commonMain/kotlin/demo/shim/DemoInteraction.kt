package demo.shim

import androidx.compose.ui.Modifier

// ==================
// MARK: demoPressable — press-state feedback modifier
// ==================

/* Fires (true) on press inside the node, (false) on release / drag-off. Native
   delegates to the project's Modifier.pressable (SDL pointer events); a future
   jvm target passes through unchanged (call sites there rely on the accompanying
   clickable + collectIsPressedAsState instead). */
expect fun Modifier.demoPressable(onChange: (Boolean) -> Unit): Modifier
