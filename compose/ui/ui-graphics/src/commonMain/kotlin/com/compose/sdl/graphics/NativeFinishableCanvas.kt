package com.compose.sdl.graphics

// ==================
// MARK: NativeFinishableCanvas
// ==================

/**
 A renderer canvas that must be flushed once drawing into it is done — some backends
 batch geometry and only submit on flush; an offscreen canvas must flush before its
 backing texture is read. Implemented by SkiaBackedCanvas (no-op — Skia save/restore
 is balanced per call). Lets renderer-agnostic code (e.g. GraphicsLayer.toImageBitmap)
 commit an offscreen render without knowing the concrete canvas type.
*/
interface NativeFinishableCanvas {
	fun finish()
}
