package androidx.compose.ui.graphics

// SDL3-path stubs for the vendored ui.graphics.ColorFilter expects.
// The renderer doesn't currently paint through a ColorFilter pipeline; these
// satisfy the expect contracts so vendored Compose code referencing
// ColorFilter compiles. The matching real-Skia actuals live in
// core/src/vendor/skikoRenderer/.../graphics/SkiaColorFilter.skiko.kt.
// Param names match the upstream `expect` declarations exactly — required
// by Kotlin's actual-resolution rule.

internal actual class NativeColorFilter

internal actual fun actualTintColorFilter(color: Color, blendMode: BlendMode): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualColorMatrixColorFilter(colorMatrix: ColorMatrix): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualLightingColorFilter(multiply: Color, add: Color): NativeColorFilter =
	NativeColorFilter()

internal actual fun actualColorMatrixFromFilter(filter: NativeColorFilter): ColorMatrix =
	ColorMatrix()
