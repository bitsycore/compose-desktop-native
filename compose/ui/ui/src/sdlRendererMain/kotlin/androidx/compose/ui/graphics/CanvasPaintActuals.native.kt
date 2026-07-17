package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.compose.sdl.graphics.ProjectCanvas
import com.compose.sdl.graphics.ProjectImageBitmap
import com.compose.sdl.graphics.ProjectPaint

// ==================
// MARK: Canvas/Paint native actuals
// ==================

// Thin actuals for the vendored Paint / Shader / ColorFilter / PathEffect /
// ImageBitmap / Canvas factories + opaque platform types. The concrete impls
// (where any) live in com.compose.sdl.graphics per FIDELITY
// relocate rule; the project pipeline does not currently use these types
// (renderers go through Brush / DrawScope), so the actuals are stubs that
// satisfy the expect contracts and let upstream-shaped consumers compile.
// When a real Paint / Canvas-based code path lands, replace the stubs here.

// ============
//  Paint
//  Typealias to Any matching skiko pattern (upstream desktop maps to
//  org.jetbrains.skia.Paint; we don't have a single backend type since
//  Skia and SDL3 renderers each carry their own paint state).

@Suppress("DEPRECATION_ERROR")
actual typealias NativePaint = Any
actual fun Paint(): Paint = ProjectPaint()

// ============
//  Shader — placeholder actuals. Skia's SkiaShader.skiko.kt is blocked
//  because it uses SkiaImageAsset.asSkiaBitmap (SkiaImageAsset is blocked
//  by K2's expect+actual same-source-set rule for
//  `internal expect fun ByteArray.putBytesInto`). SDL3 has no shader
//  pipeline — gradient rasterisation goes through
//  com.compose.sdl.graphics.GradientBridge directly, not Shader.

// SDL3 has no GPU shader stage — it samples gradients per-vertex straight from a
// Brush (see Sdl3DrawScope.samplerFor). ShaderBrush.applyTo only ever leaves a
// Shader on the Paint (and forces color = Black), so the gradient descriptor
// would otherwise be lost and Modifier.background(Brush.linearGradient(...)) would
// paint solid black. We stash the reconstructed gradient Brush here (its anchor
// points are already resolved against the draw size by createShader) so
// Sdl3Canvas.brushFor can recover it. Null for non-gradient shaders.
actual class Shader internal constructor() {
	internal var brush: Brush? = null
}

internal actual class TransformShader actual constructor() {
	actual var shader: Shader? = null
	actual fun transform(matrix: Matrix?) {}
}

// Rebuild the gradient stops as (position, color) pairs when explicit stops were
// supplied; otherwise fall back to the evenly-distributed colors overload.
private fun gradientStops(colors: List<Color>, colorStops: List<Float>?): Array<Pair<Float, Color>>? =
	if (colorStops != null && colorStops.size == colors.size)
		Array(colors.size) { colorStops[it] to colors[it] }
	else null

internal actual fun ActualLinearGradientShader(
	from: Offset,
	to: Offset,
	colors: List<Color>,
	colorStops: List<Float>?,
	tileMode: TileMode,
): Shader = Shader().also {
	val stops = gradientStops(colors, colorStops)
	it.brush = if (stops != null)
		Brush.linearGradient(*stops, start = from, end = to, tileMode = tileMode)
	else
		Brush.linearGradient(colors = colors, start = from, end = to, tileMode = tileMode)
}

internal actual fun ActualRadialGradientShader(
	center: Offset,
	radius: Float,
	colors: List<Color>,
	colorStops: List<Float>?,
	tileMode: TileMode,
): Shader = Shader().also {
	val stops = gradientStops(colors, colorStops)
	it.brush = if (stops != null)
		Brush.radialGradient(*stops, center = center, radius = radius, tileMode = tileMode)
	else
		Brush.radialGradient(colors = colors, center = center, radius = radius, tileMode = tileMode)
}

internal actual fun ActualSweepGradientShader(
	center: Offset,
	colors: List<Color>,
	colorStops: List<Float>?,
): Shader = Shader().also {
	val stops = gradientStops(colors, colorStops)
	it.brush = if (stops != null)
		Brush.sweepGradient(*stops, center = center)
	else
		Brush.sweepGradient(colors = colors, center = center)
}

internal actual fun ActualImageShader(
	image: ImageBitmap,
	tileModeX: TileMode,
	tileModeY: TileMode,
): Shader = Shader()

internal actual fun ActualCompositeShader(dst: Shader, src: Shader, blendMode: BlendMode): Shader =
	Shader()

// NativeColorFilter + actualTintColorFilter / actualColorMatrixColorFilter /
// actualLightingColorFilter / actualColorMatrixFromFilter actuals are now
// split per renderer:
//   - Skia path: vendored from upstream SkiaColorFilter.skiko.kt into
//     core/src/vendor/skikoRenderer/.../graphics/ — uses real
//     org.jetbrains.skia.ColorFilter.
//   - SDL3 path: stubs in core/src/sdlRendererMain/.../graphics/ColorFilter.sdl.kt.

// ============
//  PathEffect actuals live per-renderer:
//   * skikoRenderer: vendored SkiaBackedPathEffect.skiko.kt (Skia-backed).
//   * sdlRenderer:   SdlPathEffect.sdl.kt (no-op; SDL3 has no path-effect
//                    pipeline). Both provide the four `internal actual
//                    fun actual{Corner,Dash,Chain,Stamped}PathEffect`.

// ============
//  ImageBitmap — placeholder actuals. SkiaImageAsset.skiko.kt (real Skia
//  backing) is blocked on the same-source-set expect+actual K2 rule for
//  `ByteArray.putBytesInto`. Both renderers use ProjectImageBitmap for now.

// Delegate to the renderer's offscreen support when present (the SDL renderer
// registers a render-to-texture impl so vendored VectorPainter / DrawCache — hence
// material3's ImageVector icons — actually render). Falls back to the stub when no
// renderer registered one (nothing renders offscreen, same as before).
internal actual fun ActualImageBitmap(
	width: Int,
	height: Int,
	config: ImageBitmapConfig,
	hasAlpha: Boolean,
	colorSpace: ColorSpace,
): ImageBitmap =
	com.compose.sdl.graphics.offscreenRenderer
		?.createImageBitmap(width, height, config, hasAlpha, colorSpace)
		?: ProjectImageBitmap(width, height, config, hasAlpha, colorSpace)

internal actual fun createImageBitmap(bytes: ByteArray): ImageBitmap =
	throw UnsupportedOperationException("ImageBitmap from bytes not supported; use Res.painter")

// ============
//  Canvas — typealias matching skiko pattern.

actual typealias NativeCanvas = Any

internal actual fun ActualCanvas(image: ImageBitmap): Canvas =
	com.compose.sdl.graphics.offscreenRenderer?.createCanvas(image) ?: ProjectCanvas()

// PathMeasure actuals live per-renderer:
//   * skikoRenderer: vendored SkiaBackedPathMeasure.skiko.kt.
//   * sdlRenderer:   PathMeasure.sdl.kt (no-op — SDL3 has no path-measure).

// RenderEffect / BlurEffect / OffsetEffect actuals live per-renderer:
//   * skikoRenderer: vendored SkiaBackedRenderEffect.skiko.kt.
//   * sdlRenderer:   RenderEffect.sdl.kt (no-op).

