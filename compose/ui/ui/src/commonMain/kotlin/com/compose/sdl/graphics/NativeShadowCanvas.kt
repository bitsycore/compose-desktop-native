package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline

// ==================
// MARK: NativeShadowCanvas (render bridge)
// ==================

/* Drop-shadow bridge, same shape as NativeTextCanvas / NativePainterCanvas:
   ProjectOwnedLayer calls it when a graphicsLayer carries shadowElevation > 0
   (Modifier.shadow, m3 Surface tonal+shadow elevation). The outline is the
   layer's resolved shape in LAYER-LOCAL pixels — the canvas is already
   translated to the layer origin when this is invoked, and the shadow must
   paint BEFORE the layer's clip (it lives outside the bounds).

   Implementations:
   - SkiaCanvas — a real Gaussian blur MaskFilter on the outline.
   - Sdl3Canvas — stacked, expanding alpha rings through the tessellator
     (the SDL 2D renderer has no shader/blur primitive; alpha accumulation
     over quadratically-spaced rings + the per-ring AA fringe reads as a
     soft Gaussian at UI sizes). */
interface NativeShadowCanvas {
	fun drawDropShadow(
		inOutline: Outline,
		inElevationPx: Float,
		inAmbientColor: Color,
		inSpotColor: Color,
	)
}
