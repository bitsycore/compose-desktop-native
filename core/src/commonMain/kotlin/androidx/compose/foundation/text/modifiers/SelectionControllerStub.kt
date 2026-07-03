package androidx.compose.foundation.text.modifiers

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult

// ==================
// MARK: SelectionController — project stub
// ==================

/*
 Extracted subset of upstream foundation.text.modifiers.SelectionController.kt.
 Real one is welded to the selection engine (SelectionRegistrar / TextDragObserver
 / MouseSelectionObserver / MultiWidgetSelectionDelegate / SelectionAdjustment /
 BringIntoViewRequester) — 200+L of upstream selection plumbing we don't vendor.

 Vendored TextAnnotatedStringNode / TextStringSimpleNode reference this class
 by name for an OPTIONAL selection controller (parameter defaults to null); the
 real one wires in text-in-text-in-container selection. Our stub is a bare class
 with the two members Node.kt calls (`updateTextLayout` + `draw`) — both no-ops
 so BasicText compiles without pulling the whole selection engine.

 If the selection engine ever vendors, delete this file and let upstream provide.
*/
internal class SelectionController {
	fun updateTextLayout(@Suppress("UNUSED_PARAMETER") textLayoutResult: TextLayoutResult) {}
	fun draw(@Suppress("UNUSED_PARAMETER") scope: DrawScope) {}
}
