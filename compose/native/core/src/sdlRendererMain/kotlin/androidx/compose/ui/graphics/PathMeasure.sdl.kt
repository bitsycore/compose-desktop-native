package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset

// ==================
// MARK: PathMeasure — SDL3 renderer no-op actual
// ==================

/*
 * SDL3 counterpart of upstream `SkiaBackedPathMeasure.skiko.kt`. SDL3 has no
 * path-measurement pipeline; every method is a no-op. Nothing in the current
 * SDL3 render path calls PathMeasure, so callers get inert values.
 */
private class NoOpPathMeasure : PathMeasure {
	override val length: Float = 0f
	override fun getSegment(
		startDistance: Float,
		stopDistance: Float,
		destination: Path,
		startWithMoveTo: Boolean,
	): Boolean = false
	override fun setPath(path: Path?, forceClosed: Boolean) {}
	override fun getPosition(distance: Float): Offset = Offset.Unspecified
	override fun getTangent(distance: Float): Offset = Offset.Unspecified
}

actual fun PathMeasure(): PathMeasure = NoOpPathMeasure()
