package androidx.compose.ui.text.font

// ==================
// MARK: FontWeight
// ==================

/* Standard CSS-style weight integer (100..900) wrapped so call sites read
   naturally as e.g. FontWeight.Bold. */
class FontWeight(val weight: Int) : Comparable<FontWeight> {

	override fun compareTo(other: FontWeight): Int = weight.compareTo(other.weight)
	override fun equals(other: Any?): Boolean = other is FontWeight && other.weight == weight
	override fun hashCode(): Int = weight
	override fun toString(): String = "FontWeight(weight=$weight)"

	companion object {
		val W100 = FontWeight(100)
		val W200 = FontWeight(200)
		val W300 = FontWeight(300)
		val W400 = FontWeight(400)
		val W500 = FontWeight(500)
		val W600 = FontWeight(600)
		val W700 = FontWeight(700)
		val W800 = FontWeight(800)
		val W900 = FontWeight(900)

		val Thin       = W100
		val ExtraLight = W200
		val Light      = W300
		val Normal     = W400
		val Medium     = W500
		val SemiBold   = W600
		val Bold       = W700
		val ExtraBold  = W800
		val Black      = W900
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
   generics fall through to Default.
   NOTE: `Named` is a deliberate project extension (resolve fonts by a
   registered name) with no official Compose counterpart. */
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
