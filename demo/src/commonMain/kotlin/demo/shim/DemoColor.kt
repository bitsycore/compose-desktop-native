package demo.shim

import androidx.compose.ui.graphics.Color

// ==================
// MARK: Color helpers (pure common)
// ==================

/* The project ships blend / lighten / darken under com.compose.desktop.native
   .graphics, which a jvm target compiling against upstream Compose can't see.
   These are trivial pure-Color math, so the demo carries its own copy — keeping
   the shared shell + screens free of any project-only Color dependency. */

/* Linear blend toward [other]. amount=0 returns this; amount=1 returns other. */
fun Color.blend(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red + (other.red - red) * a,
        green + (other.green - green) * a,
        blue + (other.blue - blue) * a,
        alpha + (other.alpha - alpha) * a,
    )
}

/* Linear blend toward white. */
fun Color.lighten(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(red + (1f - red) * a, green + (1f - green) * a, blue + (1f - blue) * a, alpha)
}

/* Linear blend toward black. */
fun Color.darken(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(red * (1f - a), green * (1f - a), blue * (1f - a), alpha)
}
