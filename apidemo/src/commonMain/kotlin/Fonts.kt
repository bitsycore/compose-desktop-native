package apidemo

import androidx.compose.ui.text.font.FontFamily

// ==================
// MARK: Monospace body font (seam)
// ==================
// Family name the body editor passes for monospace text. Resolves to the
// bundled Noto Sans Mono once registered; null means the font isn't shipped
// (data.kres on native, classpath font/ on jvm), in which case the body falls
// back to the default proportional font.
const val kMonoFamily = "noto-mono"

/** The raw family name string — used by wrappedRowCount, which measures via the
   platform text pipeline (accepts a name string). Null when not bundled. */
expect val monoFontFamilyName: String?

/** The material3 Text / BasicTextField-shaped FontFamily for the mono family.
   Null when not bundled. */
expect val monoFontFamily: FontFamily?
