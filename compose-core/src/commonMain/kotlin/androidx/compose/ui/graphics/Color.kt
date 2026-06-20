package androidx.compose.ui.graphics

// ==================
// MARK: Color
// ==================

data class Color(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) {

    constructor(red: Int, green: Int, blue: Int, alpha: Int = 255)
        : this(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    constructor(argb: Long) : this(
        red = ((argb shr 16) and 0xFF).toInt(),
        green = ((argb shr 8) and 0xFF).toInt(),
        blue = (argb and 0xFF).toInt(),
        alpha = ((argb shr 24) and 0xFF).toInt()
    )

    // Byte accessors for SDL3 renderer
    val r8 get() = (red * 255).toInt().coerceIn(0, 255)
    val g8 get() = (green * 255).toInt().coerceIn(0, 255)
    val b8 get() = (blue * 255).toInt().coerceIn(0, 255)
    val a8 get() = (alpha * 255).toInt().coerceIn(0, 255)

    companion object {
        val Black = Color(0f, 0f, 0f)
        val White = Color(1f, 1f, 1f)
        val Red = Color(1f, 0f, 0f)
        val Green = Color(0f, 1f, 0f)
        val Blue = Color(0f, 0f, 1f)
        val Yellow = Color(1f, 1f, 0f)
        val Cyan = Color(0f, 1f, 1f)
        val Magenta = Color(1f, 0f, 1f)
        val Gray = Color(0.5f, 0.5f, 0.5f)
        val LightGray = Color(0.75f, 0.75f, 0.75f)
        val DarkGray = Color(0.25f, 0.25f, 0.25f)
        val Transparent = Color(0f, 0f, 0f, 0f)
        val Unspecified = Color(0f, 0f, 0f, 0f)
    }
}

// ==================
// MARK: Color helpers
// ==================

/* Linear blend toward white. amount=0 returns this; amount=1 returns white. */
fun Color.lighten(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red + (1f - red) * a,
        green + (1f - green) * a,
        blue + (1f - blue) * a,
        alpha
    )
}

/* Linear blend toward black. */
fun Color.darken(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(red * (1f - a), green * (1f - a), blue * (1f - a), alpha)
}

/* Linear blend toward an arbitrary color. amount=0 returns this; amount=1
   returns the other color. Used for Material state-layer overlays. */
fun Color.blend(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red + (other.red - red) * a,
        green + (other.green - green) * a,
        blue + (other.blue - blue) * a,
        alpha + (other.alpha - alpha) * a
    )
}
