package apidemo

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==================
// MARK: Theme - the app's legacy colour slots, derived from the active palette
// ==================
// AppColors is the app's own colour vocabulary (used by the custom widgets). It
// is now DERIVED from the selected Voltic M3 ColorScheme via appColorsFromScheme
// (VolticThemes.kt) and provided through LocalAppColors by App(). The default
// below (purple dark) only applies before that provider is installed.

class AppColors(
    val bg: Color, val panel: Color, val field: Color, val border: Color,
    val accent: Color, val text: Color, val dim: Color, val onAccent: Color,
)

internal val LocalAppColors = staticCompositionLocalOf { appColorsFromScheme(VolticPalette.Purple.scheme.dark) }
