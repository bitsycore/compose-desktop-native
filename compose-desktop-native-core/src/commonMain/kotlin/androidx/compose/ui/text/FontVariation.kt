package androidx.compose.ui.text

// ==================
// MARK: FontVariation
// ==================

/* A single axis setting for a variable font. Use the companion factories
   (Weight / Fill / Grade / OpticalSize / ItalicVariation) for the common
   Material Symbols and standard text axes; use `Axis(...)` for custom ones.

   axisTag is the 4-character OpenType axis tag (e.g. "wght", "FILL"). It is
   case-sensitive — registered axes use lowercase ("wght", "opsz"), custom
   axes use uppercase ("FILL", "GRAD"). */
data class FontVariation(val axisTag: String, val value: Float) {

	companion object {
		/* Generic registered weight axis (100..900, 400 = regular, 700 = bold). */
		fun Weight(inValue: Int): FontVariation = FontVariation("wght", inValue.toFloat())

		/* Material Symbols FILL axis (0..1). 0 = outlined, 1 = filled. */
		fun Fill(inValue: Float): FontVariation = FontVariation("FILL", inValue)

		/* Material Symbols GRAD axis (-25..200). Subtle weight adjustment
		   without changing the icon's footprint; useful for emphasis or
		   light-on-dark legibility tweaks. 0 = default. */
		fun Grade(inValue: Int): FontVariation = FontVariation("GRAD", inValue.toFloat())

		/* Material Symbols opsz axis (20..48). The size the glyph was
		   designed for; pick the value closest to the rendered size so
		   stroke thickness matches the icon's intended density. */
		fun OpticalSize(inValue: Int): FontVariation = FontVariation("opsz", inValue.toFloat())

		/* Registered italic-slant axis (-90..0 degrees). */
		fun Slant(inValue: Float): FontVariation = FontVariation("slnt", inValue)

		/* Registered width axis (50..200, 100 = normal). */
		fun Width(inValue: Float): FontVariation = FontVariation("wdth", inValue)

		/* Escape hatch for any other axis. */
		fun Axis(inTag: String, inValue: Float): FontVariation = FontVariation(inTag, inValue)
	}
}
