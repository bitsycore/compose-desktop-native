package androidx.compose.foundation

import com.compose.desktop.native.element.ClickableModifier
import androidx.compose.ui.Modifier

// ==================
// MARK: Interaction modifiers
// ==================

fun Modifier.clickable(onClick: () -> Unit) = then(ClickableModifier(onClick))

// Modifier.hoverable is now VENDORED (androidx.compose.foundation.Hoverable.kt) — the upstream
// `hoverable(interactionSource, enabled)` driven by the PointerInputEventProcessor. The old project
// `hoverable(onChange)` + HoverableModifier/HoverableNode are retired.
