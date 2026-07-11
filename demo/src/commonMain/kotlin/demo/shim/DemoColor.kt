package demo.shim

import androidx.compose.ui.graphics.Color

/** Linear blend toward [other]. `amount=0` returns this; `amount=1` returns other. */
fun Color.blend(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red + (other.red - red) * a,
        green + (other.green - green) * a,
        blue + (other.blue - blue) * a,
        alpha + (other.alpha - alpha) * a,
    )
}
