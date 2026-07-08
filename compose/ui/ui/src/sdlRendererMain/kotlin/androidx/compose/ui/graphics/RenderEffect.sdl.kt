package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset

// ==================
// MARK: RenderEffect / BlurEffect / OffsetEffect — SDL3 renderer actuals
// ==================

/*
 * SDL3 counterpart of upstream `SkiaBackedRenderEffect.skiko.kt`. SDL3 has
 * no render-effect / blur / offset filter pipeline (that would require an
 * offscreen framebuffer + shader chain), so `isSupported()` always returns
 * false and the concrete BlurEffect / OffsetEffect are inert data holders.
 */
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
