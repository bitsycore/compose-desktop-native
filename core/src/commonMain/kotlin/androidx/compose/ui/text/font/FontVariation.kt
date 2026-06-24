package androidx.compose.ui.text.font

// ==================
// MARK: FontVariation
// ==================

/* A single axis setting for a variable font. Use the companion factories
   (Weight / Fill / Grade / OpticalSize / Slant / Width) for the common
   Material Symbols and standard text axes; use `Axis(...)` for custom ones.

   axisTag is the 4-character OpenType axis tag (e.g. "wght", "FILL"). It is
   case-sensitive — registered axes use lowercase ("wght", "opsz"), custom
   axes use uppercase ("FILL", "GRAD").

   NOTE: official Compose models FontVariation as an object whose lowercase
   factories (weight/grade/italic/slant/width/opticalSizing) return a nested
   FontVariation.Setting. This project keeps a flat data-class shape with
   capitalized factories (read by the FreeType icon path); see CLAUDE.md
   "known-diverging". */
data class FontVariation(val axisTag: String, val value: Float) {

	companion object {
		/* Generic registered weight axis (100..900, 400 = regular, 700 = bold). */
		fun Weight(value: Int): FontVariation = FontVariation("wght", value.toFloat())

		/* Material Symbols FILL axis (0..1). 0 = outlined, 1 = filled. */
		fun Fill(value: Float): FontVariation = FontVariation("FILL", value)

		/* Material Symbols GRAD axis (-25..200). Subtle weight adjustment
		   without changing the icon's footprint. 0 = default. */
		fun Grade(value: Int): FontVariation = FontVariation("GRAD", value.toFloat())

		/* Material Symbols opsz axis (20..48). Pick the value closest to the
		   rendered size so stroke thickness matches the intended density. */
		fun OpticalSize(value: Int): FontVariation = FontVariation("opsz", value.toFloat())

		/* Registered italic-slant axis (-90..0 degrees). */
		fun Slant(value: Float): FontVariation = FontVariation("slnt", value)

		/* Registered width axis (50..200, 100 = normal). */
		fun Width(value: Float): FontVariation = FontVariation("wdth", value)

		/* Escape hatch for any other axis. */
		fun Axis(tag: String, value: Float): FontVariation = FontVariation(tag, value)
	}
}
