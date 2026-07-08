package com.compose.sdl.graphics

import androidx.compose.ui.layout.ContentScale
import com.compose.sdl.res.ResourceKind

// ==================
// MARK: NativePainterCanvas bridge
// ==================

/*
 Phase 9 B5 — image drawing bridge. Renderer Canvas backends (Sdl3Canvas /
 future SkiaCanvas) implement this interface to paint a bundled resource by
 path + kind. [ResourcePainter] (upstream `Painter` subclass in the res
 pipeline) casts the current Canvas to this and calls drawNativePainter from
 inside its `Painter.onDraw()` — reached via upstream `Modifier.paint(...)`
 (PainterModifier), so the resource render path is driven end-to-end by the
 vendored upstream engine.

 contentScale is FillBounds when called through PainterModifier (which
 pre-scales the size); other values are supported for legacy callers.
*/
interface NativePainterCanvas {
	fun drawNativePainter(
		inResourcePath: String,
		inKind: ResourceKind,
		inX: Float,
		inY: Float,
		inWidth: Float,
		inHeight: Float,
		inContentScale: ContentScale,
		inAlpha: Float,
	)
}
