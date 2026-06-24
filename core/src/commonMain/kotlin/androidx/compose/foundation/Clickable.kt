package androidx.compose.foundation

import androidx.compose.ui.ClickableModifier
import androidx.compose.ui.HoverableModifier
import androidx.compose.ui.Modifier

// ==================
// MARK: Interaction modifiers
// ==================

fun Modifier.clickable(onClick: () -> Unit) = then(ClickableModifier(onClick))

/* Fires (true) when the cursor enters this node, (false) when it leaves.
   Multiple hoverable ancestors of the cursor target all fire (true)
   simultaneously, matching Compose's enter/exit semantics. */
fun Modifier.hoverable(onChange: (Boolean) -> Unit) = then(HoverableModifier(onChange))
