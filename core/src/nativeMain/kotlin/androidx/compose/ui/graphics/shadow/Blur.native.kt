package androidx.compose.ui.graphics.shadow

import androidx.compose.ui.graphics.Paint

// ==================
// MARK: Blur — native actuals (no-op)
// ==================

/*
 * Actuals for the vendored commonMain `Blur.kt` (`internal expect fun
 * BlurFilter(radius)`, `internal expect class BlurFilter`, `internal expect
 * fun Paint.setBlurFilter`). Upstream's Skia actual routes to
 * `MaskFilter.makeBlur`; on SDL3 we have no software blur pipeline yet, so
 * `BlurFilter` is a plain radius holder and `Paint.setBlurFilter` is a no-op.
 *
 * Downstream Shadow.kt / DropShadowPainter / InnerShadowPainter aren't
 * vendored (blocked separately on GraphicsLayerModifier). Once they land,
 * revisit — shadow rendering will be visually silent until a real blur is
 * wired.
 *
 * The primary constructor takes zero args to avoid clashing with the
 * top-level factory `fun BlurFilter(radius)` (Kotlin considers a class
 * with a single-Float ctor and a top-level fn with a single-Float arg to
 * have the same signature).
 */
internal actual class BlurFilter internal constructor() {
	internal var radius: Float = 0f
}

internal actual fun BlurFilter(radius: Float): BlurFilter =
	BlurFilter().apply { this.radius = radius }

internal actual fun Paint.setBlurFilter(blur: BlurFilter?) {
	// no-op — SDL3 has no MaskFilter; Skia renderer would wire skiaPaint.maskFilter.
}
