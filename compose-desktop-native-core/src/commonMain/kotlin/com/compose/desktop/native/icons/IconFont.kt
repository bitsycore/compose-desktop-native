package com.compose.desktop.native.icons

// ==================
// MARK: IconFont registry
// ==================

/* Shared store of icon-font byte blobs keyed by family name. Icon-font modules
   (e.g. compose-desktop-native-material-symbols-outlined) call register() at
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

	/* Register a font family's bytes (typically loaded from the module's
	   bundled composeResources). Idempotent — a second call with the same
	   family replaces the previous bytes. */
	fun register(inFamily: String, inBytes: ByteArray) {
		fFonts[inFamily] = inBytes
	}

	/* Bytes for a registered family, or null if not registered. Renderers
	   fall back to the default font when this returns null. */
	fun bytesFor(inFamily: String): ByteArray? = fFonts[inFamily]

	val families: Set<String> get() = fFonts.keys
}
