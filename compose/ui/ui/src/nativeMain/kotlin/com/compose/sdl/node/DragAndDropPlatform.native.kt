package com.compose.sdl.node

import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.draganddrop.Sdl3DragAndDropOwner

// Native actuals for the DragAndDropPlatform hooks. The manager is the
// SDL3-backed Sdl3DragAndDropOwner; the dispatch extensions cast to it and
// forward the drop-session event, no-op'ing if some other manager is in place
// (e.g. a test fake).

internal actual fun createPlatformDragAndDropManager(): DragAndDropManager = Sdl3DragAndDropOwner()

internal actual fun DragAndDropManager.dropBegin() {
	if (this is Sdl3DragAndDropOwner) onDropBegin()
}

internal actual fun DragAndDropManager.dropPosition(x: Float, y: Float) {
	if (this is Sdl3DragAndDropOwner) onDropPosition(x, y)
}

internal actual fun DragAndDropManager.dropFile(path: String) {
	if (this is Sdl3DragAndDropOwner) onDropFile(path)
}

internal actual fun DragAndDropManager.dropText(text: String) {
	if (this is Sdl3DragAndDropOwner) onDropText(text)
}

internal actual fun DragAndDropManager.dropComplete() {
	if (this is Sdl3DragAndDropOwner) onDropComplete()
}
