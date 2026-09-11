package apidemo

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// ==================
// MARK: Monospace body font (seam)
// ==================
// Family name the body editor passes for monospace text. Resolves to the
// bundled Noto Sans Mono once registered; null means the font isn't shipped
// (data.kres on native, classpath font/ on jvm), in which case the body falls
// back to the default proportional font.
const val kMonoFamily = "noto-mono"

/** Family name the bundled proportional font registers under. */
const val kDefaultFamily = "noto-sans"

/** The raw family name string - used by wrappedRowCount, which measures via the
platform text pipeline (accepts a name string). Null when not bundled. */
expect val monoFontFamilyName: String?

/** The material3 Text / BasicTextField-shaped FontFamily for the mono family.
Null when not bundled. */
expect val monoFontFamily: FontFamily?

// ==================
// MARK: Default proportional font (seam)
// ==================
// PARITY: the native side resolves FontFamily.Default to the BUNDLED NotoSans
// (SkiaFonts.defaultTypeface is the fallback for every unresolved family), while
// stock Compose Desktop on the JVM resolves it to a SYSTEM font (Segoe UI on
// Windows, Helvetica on macOS). Different typefaces mean different ascent /
// descent / line-gap, so identical layout code puts glyphs at a different height
// inside an identically-sized text box - which is what made the SourceTag pill
// look vertically centred on JVM and off-centre on native.
//
// The two stacks must rasterise the SAME face for the comparison to mean
// anything, so the app pins its default family explicitly rather than inheriting
// whatever the platform picks. Null = the bundled font isn't available, in which
// case we fall back to the platform default and the skew returns.

/** The bundled proportional font, applied app-wide as the default text family.
Null when NotoSans isn't shipped (data.kres on native, classpath font/ on jvm). */
expect val defaultFontFamily: FontFamily?

/** Re-stamps every style in a Material 3 [Typography] with [inFamily]. M3 seeds
LocalTextStyle from the typography, so this reaches every `Text` that doesn't name
a family of its own. A null family leaves the typography untouched. */
fun Typography.withDefaultFontFamily(inFamily: FontFamily?): Typography {
    if (inFamily == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = inFamily),
        displayMedium = displayMedium.copy(fontFamily = inFamily),
        displaySmall = displaySmall.copy(fontFamily = inFamily),
        headlineLarge = headlineLarge.copy(fontFamily = inFamily),
        headlineMedium = headlineMedium.copy(fontFamily = inFamily),
        headlineSmall = headlineSmall.copy(fontFamily = inFamily),
        titleLarge = titleLarge.copy(fontFamily = inFamily),
        titleMedium = titleMedium.copy(fontFamily = inFamily),
        titleSmall = titleSmall.copy(fontFamily = inFamily),
        bodyLarge = bodyLarge.copy(fontFamily = inFamily),
        bodyMedium = bodyMedium.copy(fontFamily = inFamily),
        bodySmall = bodySmall.copy(fontFamily = inFamily),
        labelLarge = labelLarge.copy(fontFamily = inFamily),
        labelMedium = labelMedium.copy(fontFamily = inFamily),
        labelSmall = labelSmall.copy(fontFamily = inFamily),
    )
}
