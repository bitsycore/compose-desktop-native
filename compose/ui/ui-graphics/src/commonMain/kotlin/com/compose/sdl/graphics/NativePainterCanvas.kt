package com.compose.sdl.graphics

import com.compose.sdl.res.ResourceKind

// ==================
// MARK: NativePainterCanvas bridge
// ==================

/**
 Image drawing bridge. Renderer Canvas backends (SkiaBackedCanvas) implement this
 to paint a bundled resource by path + kind. [com.compose.sdl.res.ResourcePainter]
 (an upstream `Painter` subclass in the res pipeline) casts the current Canvas to
 this and calls drawNativePainter from inside its `Painter.onDraw()` — reached via
 upstream `Modifier.paint(...)` (PainterModifier), which pre-scales the
 destination size per ContentScale. So the painter always draws fill-bounds into
 the given rect, and this seam carries no ui-layout (ContentScale) dependency —
 keeping :ui-graphics free of :ui.
*/
interface NativePainterCanvas {
	fun drawNativePainter(
		inResourcePath: String,
		inKind: ResourceKind,
		inX: Float,
		inY: Float,
		inWidth: Float,
		inHeight: Float,
		inAlpha: Float,
	)
}
