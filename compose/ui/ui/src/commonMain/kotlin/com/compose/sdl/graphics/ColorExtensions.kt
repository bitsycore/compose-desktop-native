package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastCoerceIn

// ==================
// MARK: Non-official Color helpers (relocated)
// ==================

/* Project-only extensions on the upstream-vendored Color value class.
   Previously lived in androidx.compose.ui.graphics; relocated here per
   FIDELITY's "no upstream equivalent → com.compose.sdl.*" rule
   so the androidx.* namespace stays a pure mirror.

   The renderers (SkiaDrawScope, Sdl3DrawScope, FreeType*) import these as
   com.compose.sdl.graphics.r8 etc. */

// 8-bit channel accessors for the SDL3 renderer (0..255).
val Color.r8: Int get() = (red * 255f).toInt().coerceIn(0, 255)
val Color.g8: Int get() = (green * 255f).toInt().coerceIn(0, 255)
val Color.b8: Int get() = (blue * 255f).toInt().coerceIn(0, 255)
val Color.a8: Int get() = (alpha * 255f).toInt().coerceIn(0, 255)

/* Linear blend toward white. amount=0 returns this; amount=1 returns white. */
fun Color.lighten(amount: Float): Color {
	val a = amount.fastCoerceIn(0f, 1f)
	return Color(red + (1f - red) * a, green + (1f - green) * a, blue + (1f - blue) * a, alpha)
}

/* Linear blend toward black. */
fun Color.darken(amount: Float): Color {
	val a = amount.fastCoerceIn(0f, 1f)
	return Color(red * (1f - a), green * (1f - a), blue * (1f - a), alpha)
}

/* Linear blend toward an arbitrary color. amount=0 returns this; amount=1
   returns the other color. Non-official — used for Material state-layer
   overlays; official code uses lerp(...) / compositeOver(...). */
fun Color.blend(other: Color, amount: Float): Color {
	val a = amount.fastCoerceIn(0f, 1f)
	return Color(
		red + (other.red - red) * a,
		green + (other.green - green) * a,
		blue + (other.blue - blue) * a,
		alpha + (other.alpha - alpha) * a,
	)
}
