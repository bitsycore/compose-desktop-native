package androidx.compose.ui.text.font

// ==================
// MARK: Font (project stub)
// ==================

/*
 * Minimal stub of upstream's `androidx.compose.ui.text.font.Font` interface.
 * Upstream's 298-line Font.kt declares `interface Font` with `style`,
 * `weight`, `variationSettings`, plus a `FontLoadingStrategy` enum and the
 * `Font.ResourceLoader` infrastructure — all welded to the upstream text
 * loading pipeline we don't have yet.
 *
 * Vendored `FontSynthesis.kt` uses `Font` only as a typed parameter in the
 * internal `expect fun FontSynthesis.synthesizeTypeface(...)` declaration,
 * which our native actual stub never calls (no typeface synthesis path in
 * the renderer yet). So we just need the type to exist.
 *
 * Replace with the vendored upstream `Font.kt` when the typeface engine
 * lands.
 */
interface Font {
	val weight: FontWeight get() = FontWeight.Normal
	val style: FontStyle get() = FontStyle.Normal
}
