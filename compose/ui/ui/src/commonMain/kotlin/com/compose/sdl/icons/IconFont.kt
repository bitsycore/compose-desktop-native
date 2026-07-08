package com.compose.sdl.icons

// ==================
// MARK: IconFont registry
// ==================

/* Shared store of icon-font byte blobs keyed by family name. Icon-font modules
   (e.g. :material-symbols:outlined) call register() at
   app start with their bundled font bytes; the active RenderBackend's text
   renderer consults bytesFor() the first time it sees a Text/Icon that asks
   for that family, opens a typeface, and caches it per (family, size).

   This lives outside androidx.compose.* because it's a project-original
   abstraction — upstream Compose Multiplatform uses Font(R.font.x) /
   FontFamily for the same purpose, but that machinery is too heavy for this
   subset (it pulls in real ImageVector + resource codegen). The renderer-
   level fontFamily on LayoutNode is just a String, and this registry tells
   the renderer where to find the bytes. */
object IconFont {

	private val fFonts = mutableMapOf<String, ByteArray>()
	// Families that render as single variable-axis glyphs (Material Symbols),
	// vs ordinary text fonts. The SDL3 renderer routes only these through its
	// single-glyph FreeType icon path; text families (e.g. a bundled
	// monospace) must go through normal full-string rendering.
	private val fIconFamilies = mutableSetOf<String>()

	/* Register a TEXT font family's bytes (rendered as full strings; e.g. a
	   bundled monospace). Idempotent — a second call with the same family
	   replaces the previous bytes. */
	fun register(inFamily: String, inBytes: ByteArray) {
		fFonts[inFamily] = inBytes
	}

	/* Register an ICON font family (Material Symbols et al.) — variable-axis,
	   drawn one glyph at a time on the SDL3 backend. Same byte store as
	   register(), but also flags the family as an icon font. */
	fun registerIcon(inFamily: String, inBytes: ByteArray) {
		fFonts[inFamily] = inBytes
		fIconFamilies += inFamily
	}

	/* Bytes for a registered family, or null if not registered. Renderers
	   fall back to the default font when this returns null. */
	fun bytesFor(inFamily: String): ByteArray? = fFonts[inFamily]

	/* True only for families registered via registerIcon() — icon fonts that
	   need single-glyph (variable-axis) rendering, not text fonts. */
	fun isIconFamily(inFamily: String): Boolean = inFamily in fIconFamilies

	val families: Set<String> get() = fFonts.keys
}
