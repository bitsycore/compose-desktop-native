package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.compose.desktop.native.graphics.ProjectCanvas
import com.compose.desktop.native.graphics.ProjectImageBitmap
import com.compose.desktop.native.graphics.ProjectPaint

// ==================
// MARK: Canvas/Paint native actuals
// ==================

/* Thin actuals for the vendored Paint / Shader / ColorFilter / PathEffect /
   ImageBitmap / Canvas factories + opaque platform types. The concrete impls
   (where any) live in com.compose.desktop.native.graphics per FIDELITY
   relocate rule; the project pipeline does not currently use these types
   (renderers go through Brush/DrawScope), so the actuals are stubs that
   satisfy the expect contracts and let upstream-shaped consumers compile.
   When a real Paint/Canvas-based code path lands, replace the stubs here. */

// ============
//  Paint
//  Typealias to Any matching skiko pattern (upstream desktop maps to
//  org.jetbrains.skia.Paint; we don't have a single backend type since
//  Skia and SDL3 renderers each carry their own paint state).

@Suppress("DEPRECATION_ERROR")
actual typealias NativePaint = Any
actual fun Paint(): Paint = ProjectPaint()

// ============
//  Shader

actual class Shader internal constructor()

internal actual class TransformShader actual constructor() {
	actual var shader: Shader? = null
	actual fun transform(matrix: Matrix?) {}
}

internal actual fun ActualLinearGradientShader(
	from: Offset,
	to: Offset,
	colors: List<Color>,
	colorStops: List<Float>?,
	tileMode: TileMode,
): Shader = Shader()

internal actual fun ActualRadialGradientShader(
	center: Offset,
	radius: Float,
	colors: List<Color>,
	colorStops: List<Float>?,
	tileMode: TileMode,
): Shader = Shader()

internal actual fun ActualSweepGradientShader(
	center: Offset,
	colors: List<Color>,
	colorStops: List<Float>?,
): Shader = Shader()

internal actual fun ActualImageShader(
	image: ImageBitmap,
	tileModeX: TileMode,
	tileModeY: TileMode,
): Shader = Shader()

internal actual fun ActualCompositeShader(dst: Shader, src: Shader, blendMode: BlendMode): Shader =
	Shader()

// ============
//  ColorFilter

internal actual class NativeColorFilter

internal actual fun actualTintColorFilter(color: Color, blendMode: BlendMode): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualColorMatrixColorFilter(colorMatrix: ColorMatrix): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualLightingColorFilter(multiply: Color, add: Color): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualColorMatrixFromFilter(filter: NativeColorFilter): ColorMatrix =
	ColorMatrix()

// ============
//  PathEffect

private class StubPathEffect : PathEffect

internal actual fun actualCornerPathEffect(radius: Float): PathEffect = StubPathEffect()

internal actual fun actualDashPathEffect(intervals: FloatArray, phase: Float): PathEffect =
	StubPathEffect()

internal actual fun actualChainPathEffect(outer: PathEffect, inner: PathEffect): PathEffect =
	StubPathEffect()

internal actual fun actualStampedPathEffect(
	shape: Path,
	advance: Float,
	phase: Float,
	style: StampedPathEffectStyle,
): PathEffect = StubPathEffect()

// ============
//  ImageBitmap

internal actual fun ActualImageBitmap(
	width: Int,
	height: Int,
	config: ImageBitmapConfig,
	hasAlpha: Boolean,
	colorSpace: ColorSpace,
): ImageBitmap = ProjectImageBitmap(width, height, config, hasAlpha, colorSpace)

internal actual fun createImageBitmap(bytes: ByteArray): ImageBitmap =
	throw UnsupportedOperationException("ImageBitmap from bytes not supported; use Res.painter")

// ============
//  Canvas — typealias matching skiko pattern.

actual typealias NativeCanvas = Any

internal actual fun ActualCanvas(image: ImageBitmap): Canvas = ProjectCanvas()

// ============
//  PathMeasure

private class StubPathMeasure : PathMeasure {
	override val length: Float = 0f
	override fun getSegment(startDistance: Float, stopDistance: Float, destination: Path, startWithMoveTo: Boolean): Boolean = false
	override fun setPath(path: Path?, forceClosed: Boolean) {}
	override fun getPosition(distance: Float): Offset = Offset.Unspecified
	override fun getTangent(distance: Float): Offset = Offset.Unspecified
}

actual fun PathMeasure(): PathMeasure = StubPathMeasure()

// ============
//  RenderEffect / BlurEffect / OffsetEffect

actual sealed class RenderEffect actual constructor() {
	actual open fun isSupported(): Boolean = false
}

actual class BlurEffect actual constructor(
	renderEffect: RenderEffect?,
	radiusX: Float,
	radiusY: Float,
	edgeTreatment: TileMode,
) : RenderEffect()

actual class OffsetEffect actual constructor(
	renderEffect: RenderEffect?,
	offset: Offset,
) : RenderEffect()

