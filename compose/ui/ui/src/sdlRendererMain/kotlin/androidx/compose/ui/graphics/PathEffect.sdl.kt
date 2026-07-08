package androidx.compose.ui.graphics

// ==================
// MARK: PathEffect — SDL3 renderer no-op actuals
// ==================

/*
 * SDL3 counterpart of upstream `SkiaBackedPathEffect.skiko.kt` (vendored into
 * `vendor/skikoRenderer/`). SDL3 has no path-effect pipeline, so every actual
 * returns a shared no-op marker. Downstream `Paint.pathEffect = …` writes are
 * accepted by the renderer but never modify the stroke geometry.
 */
private object NoOpPathEffect : PathEffect

internal actual fun actualCornerPathEffect(radius: Float): PathEffect = NoOpPathEffect

internal actual fun actualDashPathEffect(intervals: FloatArray, phase: Float): PathEffect =
	NoOpPathEffect

internal actual fun actualChainPathEffect(outer: PathEffect, inner: PathEffect): PathEffect =
	NoOpPathEffect

internal actual fun actualStampedPathEffect(
	shape: Path,
	advance: Float,
	phase: Float,
	style: StampedPathEffectStyle,
): PathEffect = NoOpPathEffect
