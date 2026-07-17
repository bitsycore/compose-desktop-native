package com.compose.sdl.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

// ==================
// MARK: NativeRenderNode — the renderer-agnostic retained display-list node
// ==================

/**
 * The one genuinely renderer-specific piece of the retained-layer engine. This is
 * the [GraphicsLayer] façade's backing node — the analogue of skiko's
 * `org.jetbrains.skiko.node.RenderNode`. [GraphicsLayer] (our copy-edit of upstream
 * `SkiaGraphicsLayer.skiko.kt`) mirrors every visual property onto this node, calls
 * [record] to capture the layer's drawing once, and [drawInto] to replay it every
 * frame with the transform / clip / shadow / compositing applied — WITHOUT
 * re-recording.
 *
 * Property contract mirrors skiko `RenderNode` (translation/scale/rotation/pivot/
 * alpha/shadow/clip/…), so the Skia actual wraps skiko `RenderNode` almost verbatim
 * and the SDL actual (`SdlRenderNode`) implements the same contract.
 *
 * [record]/[drawInto] are higher-level than skiko's `beginRecording()`/`drawInto()`
 * so BOTH a display-list impl (Skia: record into a skiko RenderNode; SDL later: a
 * cached-geometry / offscreen display list) AND the current deferred-replay impl
 * (SDL now: store the block, replay it under the transform) satisfy them.
 */
internal interface NativeRenderNode {

	// ============
	//  Geometry — where the recorded content sits and its transform origin.

	var topLeft: IntOffset
	var size: IntSize
	var pivot: Offset

	// ============
	//  Transform — applied at replay, no re-record (matches skiko RenderNode).

	var alpha: Float
	var scaleX: Float
	var scaleY: Float
	var translationX: Float
	var translationY: Float
	var rotationX: Float
	var rotationY: Float
	var rotationZ: Float
	var cameraDistance: Float

	// ============
	//  Elevation shadow.

	var shadowElevation: Float
	var ambientShadowColor: Color
	var spotShadowColor: Color

	// ============
	//  Compositing — decides an offscreen (requiresLayer) and how content composites.

	var blendMode: BlendMode
	var colorFilter: ColorFilter?
	var renderEffect: RenderEffect?
	var compositingStrategy: CompositingStrategy

	// ============
	//  Clip — outline pushed onto the node; rect / rounded-rect / generic path.

	var clip: Boolean
	fun setClipRect(left: Float, top: Float, right: Float, bottom: Float)
	fun setClipRRect(left: Float, top: Float, right: Float, bottom: Float, radii: FloatArray)
	fun setClipPath(path: Path?)

	// ============
	//  Record / replay.

	/** Capture the layer content once; re-called only when the layer is dirty. */
	fun record(density: Density, layoutDirection: LayoutDirection, size: IntSize, block: DrawScope.() -> Unit)

	/** Replay the recorded content onto [canvas], applying this node's transform. */
	fun drawInto(canvas: Canvas)

	/** Release native resources (textures / picture / display list). */
	fun close()
}

/**
 * Per-window context shared by the [NativeRenderNode]s of one composition — the
 * analogue of skiko's `RenderNodeContext`. Minimal today; the SDL caching impl will
 * grow it to carry the renderer handle it needs to allocate offscreen targets.
 */
internal class NativeRenderNodeContext(
	val measureDrawBounds: Boolean = false,
)

