package com.compose.sdl.graphics

// ==================
// MARK: NativeFinishableCanvas
// ==================

/**
 A renderer canvas that must be flushed once drawing into it is done — SDL batches
 geometry and only submits on flush; an offscreen SDL canvas must flush before its
 backing texture is read. Implemented by Sdl3Canvas (real flush) and SkiaBackedCanvas
 (no-op — Skia save/restore is balanced per call). Lets renderer-agnostic code
 (e.g. GraphicsLayer.toImageBitmap) commit an offscreen render without knowing the
 concrete canvas type.
*/
interface NativeFinishableCanvas {
	fun finish()
}
