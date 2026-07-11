package com.compose.sdl.node

import androidx.compose.ui.draganddrop.DragAndDropManager

// ==================
// MARK: DragAndDropPlatform — SDL event pump hooks
// ==================

/* Platform hooks the :window layer's event loop uses to feed SDL_EVENT_DROP_*
 * into a per-window DragAndDropManager. The manager instance is created here
 * (native actual: Sdl3DragAndDropOwner over the SDL drop stream); each dropX
 * extension casts to the concrete platform implementation and no-ops if the
 * target isn't drag-and-drop-capable.
 *
 * Kept as top-level extensions rather than methods on DragAndDropManager
 * because that interface is vendored — the pump surface stays a project
 * concern. */

internal expect fun createPlatformDragAndDropManager(): DragAndDropManager

internal expect fun DragAndDropManager.dropBegin()
internal expect fun DragAndDropManager.dropPosition(x: Float, y: Float)
internal expect fun DragAndDropManager.dropFile(path: String)
internal expect fun DragAndDropManager.dropText(text: String)
internal expect fun DragAndDropManager.dropComplete()
