package androidx.compose.ui.graphics

// ==================
// MARK: PathEffect — SDL3 renderer no-op actuals
// ==================

/*
 * SDL3 counterpart of upstream `SkiaBackedPathEffect.skiko.kt` (vendored into
 * `vendor/skikoRenderer/`). SDL3 has no path-effect pipeline, so corner/chain/
 * stamped return a shared no-op marker. Dash IS honoured: it carries its pattern
 * so the stroke tessellator (Sdl3DrawScope) can split geometry into dashes.
 */
private object NoOpPathEffect : PathEffect

// Dash pattern (on/off run lengths + phase) read by Sdl3DrawScope.dashPolyline.
internal class DashPathEffect(val intervals: FloatArray, val phase: Float) : PathEffect

internal actual fun actualCornerPathEffect(radius: Float): PathEffect = NoOpPathEffect

internal actual fun actualDashPathEffect(intervals: FloatArray, phase: Float): PathEffect =
	// Skia requires an even, positive-sum interval list; otherwise it's a no-op stroke.
	if (intervals.isNotEmpty() && intervals.all { it >= 0f } && intervals.sum() > 0f) {
		DashPathEffect(intervals, phase)
	} else NoOpPathEffect

internal actual fun actualChainPathEffect(outer: PathEffect, inner: PathEffect): PathEffect =
	NoOpPathEffect

internal actual fun actualStampedPathEffect(
	shape: Path,
	advance: Float,
	phase: Float,
	style: StampedPathEffectStyle,
): PathEffect = NoOpPathEffect
