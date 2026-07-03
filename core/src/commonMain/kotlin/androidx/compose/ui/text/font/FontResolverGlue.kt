package androidx.compose.ui.text.font

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

// ==================
// MARK: FontFamily.Resolver glue (project)
// ==================

/*
 The vendored text engine (MultiParagraph / ParagraphIntrinsics / TextLayoutResult) threads a
 FontFamily.Resolver from createFontFamilyResolver(...) down to the Paragraph factory. Upstream's
 real resolver (FontFamilyResolver.kt) is a Skia-typeface caching engine we can't use on SDL, so
 this is a name-based no-op: SdlParagraph reads style.fontFamily directly, so the resolved
 "typeface" is never consulted for measurement/paint. resolve() only feeds the deprecated
 TextLayoutResult.load() path, so a placeholder value is fine.
*/

/** Placeholder typeface — the SDL text path is name-based, so nothing reads this. */
private val kPlaceholderTypeface: State<Any> = mutableStateOf(Unit)

private object SdlFontFamilyResolver : FontFamily.Resolver {
	override fun resolve(
		fontFamily: FontFamily?,
		fontWeight: FontWeight,
		fontStyle: FontStyle,
		fontSynthesis: FontSynthesis,
	): State<Any> = kPlaceholderTypeface
}

fun createFontFamilyResolver(): FontFamily.Resolver = SdlFontFamilyResolver

@Suppress("DEPRECATION", "UNUSED_PARAMETER")
fun createFontFamilyResolver(resourceLoader: Font.ResourceLoader): FontFamily.Resolver = SdlFontFamilyResolver

/** Upstream `Font.toFontFamily()` — a single Font becomes a one-font family. Only the deprecated
 *  load() path uses it; the name-based renderer ignores the returned family. */
fun Font.toFontFamily(): FontFamily = FontFamily.Default
