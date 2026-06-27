package androidx.compose.ui.text.font

// FontWeight + FontStyle live in their own vendored files
// (androidx.compose.ui.text.font.{FontWeight,FontStyle}).

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
