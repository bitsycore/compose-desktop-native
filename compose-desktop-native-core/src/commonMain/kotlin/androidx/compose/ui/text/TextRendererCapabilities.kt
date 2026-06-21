package androidx.compose.ui.text

// ==================
// MARK: TextRendererCapabilities
// ==================

/* Lightweight capability flag that the active text renderer publishes at
   startup so library code can warn when it asks for something the renderer
   can't deliver. Today the only consumer is the Material Symbols install()
   path, which emits a one-shot warning when variable-font axes won't be
   honoured. The Skia renderer sets supportsFontVariations = true via
   Typeface.makeClone; the SDL3 renderer leaves it false because SDL3_ttf
   3.2 has no axis-set API (filename / iostream / size / face / dpi only).

   `supportsFontVariations` is null until a renderer initialises — code
   that warns on install() ignores the null case so it doesn't fire when
   the user calls install() before composeWindow() has spun up. */
object TextRendererCapabilities {

	var supportsFontVariations: Boolean? = null
}
