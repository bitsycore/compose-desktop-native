package androidx.compose.ui.text

// ==================
// MARK: FontWeight
// ==================

/* Standard CSS-style weight integer (100..900) wrapped in a value class
   so call sites read naturally as e.g. FontWeight.Bold. */
class FontWeight(val weight: Int) {

	override fun equals(other: Any?): Boolean = other is FontWeight && other.weight == weight
	override fun hashCode(): Int = weight
	override fun toString(): String = "FontWeight($weight)"

	companion object {
		val Thin       = FontWeight(100)
		val ExtraLight = FontWeight(200)
		val Light      = FontWeight(300)
		val Normal     = FontWeight(400)
		val Medium     = FontWeight(500)
		val SemiBold   = FontWeight(600)
		val Bold       = FontWeight(700)
		val ExtraBold  = FontWeight(800)
		val Black      = FontWeight(900)
	}
}

// ==================
// MARK: FontStyle
// ==================

enum class FontStyle { Normal, Italic }

// ==================
// MARK: FontFamily
// ==================

/* Declarative font selector. The renderer turns this into a concrete
   font lookup against the loaded font table. Today the SDL3 / Skia
   text renderers only resolve `Default` (the bundled Roboto) and
   custom-name registrations; the SansSerif / Serif / Monospace
   generics fall through to Default. */
sealed class FontFamily {
	object Default    : FontFamily()
	object SansSerif  : FontFamily()
	object Serif      : FontFamily()
	object Monospace  : FontFamily()
	object Cursive    : FontFamily()
	class Named(val name: String) : FontFamily() {
		override fun equals(other: Any?): Boolean = other is Named && other.name == name
		override fun hashCode(): Int = name.hashCode()
	}
}
