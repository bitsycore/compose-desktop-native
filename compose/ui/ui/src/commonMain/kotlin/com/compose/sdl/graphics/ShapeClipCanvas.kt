package com.compose.sdl.graphics

import androidx.compose.ui.geometry.RoundRect

// ==================
// MARK: NativeShapeClipCanvas bridge
// ==================

/*
 Side-channel for renderer Canvas backends that can clip subsequent drawing to
 a rounded-rect / circle OUTLINE (not just its bounding box).

 The Skia backend does NOT implement this: Skia's `Canvas.clipPath` already
 clips to a rounded path natively, so `ProjectOwnedLayer` just builds a path and
 calls `clipPath` there. The SDL3 backend has no path-clip primitive (only a
 rectangular render clip), so `Sdl3Canvas` implements this by rendering the
 clipped subtree into an offscreen target and masking it to the rounded shape.

 `ProjectOwnedLayer.drawLayer` checks `canvas is NativeShapeClipCanvas` when a
 layer has an `Outline.Rounded` clip and routes here; otherwise it falls back to
 `clipPath`. The pushed clip is scoped to the enclosing `Canvas.save()` /
 `Canvas.restore()` — the backend composites and pops it on the matching
 `restore()`.
*/
interface NativeShapeClipCanvas {
	// Pushes a rounded-rect clip for subsequent draws until the matching
	// Canvas.restore(). [inRoundRect] is in the CURRENT canvas-local coordinate
	// space (the same space draw calls use after the enclosing translate).
	fun clipRoundRect(inRoundRect: RoundRect)
}
