package androidx.compose.ui.graphics

import androidx.compose.ui.util.fastCoerceIn

// ==================
// MARK: Non-official Color helpers
// ==================

/* Project-only extensions on the official (vendored) Color value class. These
   are NOT part of androidx.compose.ui.graphics; they back the SDL3 renderer's
   8-bit channel reads and Material's state-layer overlays. Kept here (rather
   than in com.compose.desktop.native) only because the renderers already read
   them as Color.r8 etc. */

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
