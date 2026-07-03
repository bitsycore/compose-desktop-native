package androidx.compose.foundation.gestures

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed as uiChangedToDownIgnoreConsumed

// Upstream declares foundation.gestures.changedToDownIgnoreConsumed in
// IndirectPointerInputDragCycleDetector.kt (indirect-pointer engine, unvendored). Clickable only
// needs the direct-pointer meaning, so delegate to the vendored ui.input.pointer version.
internal fun PointerInputChange.changedToDownIgnoreConsumed(): Boolean = uiChangedToDownIgnoreConsumed()
