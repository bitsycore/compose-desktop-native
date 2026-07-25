package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline

// ==================
// MARK: NativeShadowCanvas (render bridge)
// ==================

/** Drop-shadow bridge, same shape as NativePainterCanvas:
   the render nodes call it when a layer carries shadowElevation > 0
   (Modifier.shadow, m3 Surface tonal+shadow elevation). The outline is the
   layer's resolved shape in LAYER-LOCAL pixels — the canvas is already
   translated to the layer origin when this is invoked, and the shadow must
   paint BEFORE the layer's clip (it lives outside the bounds).

   Implementations:
   - SkiaBackedCanvas — a real Gaussian blur MaskFilter on the outline. */
interface NativeShadowCanvas {
	fun drawDropShadow(
		inOutline: Outline,
		inElevationPx: Float,
		inAmbientColor: Color,
		inSpotColor: Color,
	)
}
