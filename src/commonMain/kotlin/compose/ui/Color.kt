package compose.ui

// ==================
// MARK: Color
// ==================

data class Color(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {

    constructor(r: Int, g: Int, b: Int, a: Int = 255)
        : this(r / 255f, g / 255f, b / 255f, a / 255f)

    constructor(argb: Long) : this(
        r = ((argb shr 16) and 0xFF).toInt(),
        g = ((argb shr 8) and 0xFF).toInt(),
        b = (argb and 0xFF).toInt(),
        a = ((argb shr 24) and 0xFF).toInt()
    )

    val r8 get() = (r * 255).toInt().coerceIn(0, 255)
    val g8 get() = (g * 255).toInt().coerceIn(0, 255)
    val b8 get() = (b * 255).toInt().coerceIn(0, 255)
    val a8 get() = (a * 255).toInt().coerceIn(0, 255)

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
    }
}
