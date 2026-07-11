package androidx.compose.ui.draganddrop

import androidx.compose.ui.geometry.Offset

// ==================
// MARK: DragAndDrop actuals — SDL3-backed
// ==================

/* Actuals for the vendored `DragAndDrop.kt` expect classes.
 * Backed by SDL3's SDL_EVENT_DROP_* stream (file / text / begin / position /
 * complete) — see Sdl3DragAndDropOwner + SDL3EventMapper for the pump side.
 *
 * DragAndDropTransferData carries the payload a drop into the window
 * delivered: zero or more file paths (SDL_EVENT_DROP_FILE) plus an optional
 * text blob (SDL_EVENT_DROP_TEXT). Both are exposed as public accessors so
 * DragAndDropTarget.onDrop implementations can pick whichever field applies.
 *
 * DragAndDropEvent bundles a transfer with the drop's window-space position
 * (in physical pixels — Option-B density flow, same coordinate frame as
 * layout). `positionInRoot` is the internal expect the vendored routing
 * reads to hit-test against target bounds.
 *
 * Both constructors are `internal` — apps never build these; SDL events do. */

/** Payload delivered by a drop into the window. `text` is non-null when the
 *  drop carried a text/plain fragment; `filePaths` is non-empty when files
 *  were dropped. Both may be present when the OS advertises multiple flavours. */
actual class DragAndDropTransferData internal constructor(
    val filePaths: List<String>,
    val text: String?,
) {
    val hasFiles: Boolean get() = filePaths.isNotEmpty()
    val hasText: Boolean get() = text != null

    internal companion object {
        internal val Empty = DragAndDropTransferData(emptyList(), null)
    }
}

/** A single drag-and-drop event delivered to the composition. [transferData]
 *  carries the payload; [position] is where the pointer sat in window pixels
 *  when the event fired. */
actual class DragAndDropEvent internal constructor(
    val position: Offset,
    val transferData: DragAndDropTransferData,
)

internal actual val DragAndDropEvent.positionInRoot: Offset get() = position
