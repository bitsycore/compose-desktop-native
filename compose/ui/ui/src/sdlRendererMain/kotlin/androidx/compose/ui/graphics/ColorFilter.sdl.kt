package androidx.compose.ui.graphics

// SDL3-path actuals for the vendored ui.graphics.ColorFilter expects.
// The public ColorFilter subclasses (BlendModeColorFilter / ColorMatrixColorFilter /
// LightingColorFilter) already carry their parameters, so the SDL renderer reads
// the filter straight off the Paint at draw time (Sdl3DrawScope.applyColorFilter).
// NativeColorFilter only needs to round-trip the ColorMatrix back out for
// ColorMatrixColorFilter.copyColorMatrix() when a filter was built from a native
// handle. The matching real-Skia actuals live in
// core/src/vendor/skikoRenderer/.../graphics/SkiaColorFilter.skiko.kt.
// Param names match the upstream `expect` declarations exactly — required
// by Kotlin's actual-resolution rule.

internal actual class NativeColorFilter(val colorMatrix: ColorMatrix? = null)

internal actual fun actualTintColorFilter(color: Color, blendMode: BlendMode): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualColorMatrixColorFilter(colorMatrix: ColorMatrix): NativeColorFilter =
	NativeColorFilter(colorMatrix)

internal actual fun actualLightingColorFilter(multiply: Color, add: Color): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualColorMatrixFromFilter(filter: NativeColorFilter): ColorMatrix =
	filter.colorMatrix ?: ColorMatrix()
