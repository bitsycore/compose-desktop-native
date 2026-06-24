package androidx.compose.ui.layout

import androidx.compose.ui.GloballyPositionedModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnSizeChangedModifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

// ==================
// MARK: onSizeChanged / onGloballyPositioned
// ==================

/* Fires whenever the modified node's measured size changes. State writes in
   the callback schedule a recomposition next frame. */
fun Modifier.onSizeChanged(onSizeChanged: (IntSize) -> Unit) =
	then(OnSizeChangedModifier(onSizeChanged))

/* Fires after each layout pass with this node's absolute (window-level)
   coordinates whenever they change. Use it to anchor overlay popups to a
   moving target. NOTE: official passes a LayoutCoordinates; this reduced form
   passes the IntOffset position (see CLAUDE.md known-diverging). */
fun Modifier.onGloballyPositioned(onGloballyPositioned: (IntOffset) -> Unit) =
	then(GloballyPositionedModifier(onGloballyPositioned))
