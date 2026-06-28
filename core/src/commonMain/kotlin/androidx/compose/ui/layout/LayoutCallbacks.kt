package androidx.compose.ui.layout

import com.compose.desktop.native.element.GloballyPositionedModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

// ==================
// MARK: onGloballyPositioned
// ==================

// `Modifier.onSizeChanged` lives in the vendored
// `androidx.compose.ui.layout.OnRemeasuredModifier` upstream file —
// don't redefine it here or call sites get overload-ambiguity errors.

/**
 * Fires after each layout pass with this node's absolute (window-level)
 * coordinates whenever they change. Use it to anchor overlay popups to a
 * moving target.
 *
 * NOTE: official passes a [LayoutCoordinates]; this reduced form passes the
 * [IntOffset] position (see CLAUDE.md known-diverging).
 */
fun Modifier.onGloballyPositioned(onGloballyPositioned: (IntOffset) -> Unit) =
	then(GloballyPositionedModifier(onGloballyPositioned))
