package androidx.compose.ui.draganddrop

import androidx.compose.ui.geometry.Offset

// ==================
// MARK: DragAndDrop actuals — native no-op
// ==================

/*
 Actuals for vendored DragAndDrop.kt. Mirrors upstream's macosMain actual —
 event / transfer-data classes are private-ctor placeholders today.

 TODO: real cross-platform DnD via SDL3 — SDL_EVENT_DROP_FILE /
 SDL_EVENT_DROP_TEXT / SDL_EVENT_DROP_BEGIN / SDL_EVENT_DROP_COMPLETE can back
 both DragAndDropEvent (drop payload + position) and DragAndDropTransferData
 (source-side data). When wired, ComposeWindow's pollEvents feeds
 SDL_EVENT_DROP_* into a DND session and ComposeSceneDragAndDropNode-shape
 routing drives the vendored DragAndDropTarget nodes. See NODE_ENGINE_PORT.md.
*/

actual class DragAndDropEvent private constructor()

internal actual val DragAndDropEvent.positionInRoot: Offset
	get() = Offset.Zero

actual class DragAndDropTransferData private constructor()
