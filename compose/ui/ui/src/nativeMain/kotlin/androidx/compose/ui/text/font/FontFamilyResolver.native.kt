package androidx.compose.ui.text.font

// ==================
// MARK: FontFamilyResolver — native actuals for vendored expects
// ==================

/*
 * Project-side actuals for the vendored `FontFamilyResolver.kt` (commonMain):
 *   * `internal expect class PlatformFontFamilyTypefaceAdapter()` — the "everything-
 *     that-isn't-a-FontListFontFamily" adapter. Upstream's skiko actual routes
 *     `Default`, `SansSerif`, `Serif`, `Monospace`, `Cursive`, and `LoadedFontFamily`
 *     through `SkiaFontLoader` to produce a real `SkTypeface`. Our text renderer
 *     (SdlParagraph / SkiaParagraph) reads `TextStyle.fontFamily` and
 *     [com.compose.sdl.text.NamedFont] directly to look the family up
 *     against its registered font table, so the resolver's typeface is never
 *     consulted. A no-op adapter (returns an [TypefaceResult.Immutable] wrapping
 *     `Unit`) is enough to keep the resolver stack compiling / not throwing.
 *   * `fun createFontFamilyResolver()` — top-level factory declared in
 *     `FontFamilyResolver.skiko.kt` upstream. Provides an [FontFamilyResolverImpl]
 *     with a project [SdlPlatformFontLoader] + default resolve interceptor +
 *     the no-op platform adapter above.
 *
 * TODO: wire a real SDL3 typeface pipeline (register bytes → font handle,
 *   resolve via TextMeasurer's registered names) so `TypefaceResult.Immutable.value`
 *   becomes a usable handle. Today it's a stand-in — only the deprecated
 *   `TextLayoutResult.load()` path reads `.value`.
 */

@Suppress("DEPRECATION")
private class SdlPlatformFontLoader : PlatformFontLoader {
	override val cacheKey: Any? = null
	override fun loadBlocking(font: Font): Any? = Unit
	override suspend fun awaitLoad(font: Font): Any? = Unit
}

private val kSdlPlatformFontLoader = SdlPlatformFontLoader()

internal actual class PlatformFontFamilyTypefaceAdapter actual constructor() :
	FontFamilyTypefaceAdapter {
	actual override fun resolve(
		typefaceRequest: TypefaceRequest,
		platformFontLoader: PlatformFontLoader,
		onAsyncCompletion: (TypefaceResult.Immutable) -> Unit,
		createDefaultTypeface: (TypefaceRequest) -> Any,
	): TypefaceResult? {
		if (typefaceRequest.fontFamily is FontListFontFamily) return null
		// Return a placeholder typeface — renderer reads `TextStyle.fontFamily`
		// directly (via NamedFont / FontListFontFamily) and never consumes `.value`.
		return TypefaceResult.Immutable(Unit)
	}
}

/** Top-level factory upstream declares in `FontFamilyResolver.skiko.kt`; we place
 *  the project actual in nativeMain since our loader isn't Skia-backed. */
fun createFontFamilyResolver(): FontFamily.Resolver =
	FontFamilyResolverImpl(
		platformFontLoader = kSdlPlatformFontLoader,
	)

/** Bridge for the deprecated `Font.ResourceLoader → FontFamily.Resolver` path
 *  (upstream declares this in `DelegatingFontLoaderForDeprecatedUsage.kt`).
 *  Our renderer never consults the returned typeface, so we can ignore the
 *  supplied loader entirely. */
@Suppress("DEPRECATION", "UNUSED_PARAMETER", "KmpDeprecationMismatch")
@Deprecated(
	"This exists to bridge existing Font.ResourceLoader APIs, and should be removed with them",
	replaceWith = ReplaceWith("createFontFamilyResolver()"),
)
internal actual fun createFontFamilyResolver(
	fontResourceLoader: Font.ResourceLoader,
): FontFamily.Resolver = createFontFamilyResolver()
