package apidemo

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==================
// MARK: Voltic themes — selectable Material 3 palettes
// ==================
// The three "voltic" brand palettes (Kotlin purple, orange, and a stock-M3
// purple), each a full light + dark M3 ColorScheme plus an EXTENDED group that
// M3 has no slot for (warning). The app runs on MaterialTheme(colorScheme = …)
// driven by the selected palette; the legacy AppColors abstraction is DERIVED
// from the active scheme (appColorsFromScheme) so existing widgets follow along.

/** Colors M3's ColorScheme has no slot for. Warning is the amber "unsaved /
undefined variable" accent; read it with VolticTheme.extended.warning. */
@Immutable
data class VolticExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

/** One palette's four schemes: the M3 light/dark ColorSchemes + the matching
extended groups. */
@Immutable
class VolticScheme(
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightExtended: VolticExtendedColors,
    val darkExtended: VolticExtendedColors,
)

/** The palettes the user can pick from. `id` is the stable persistence key. */
enum class VolticPalette(val id: String, val label: String) {
    Purple("purple", "Kotlin Purple"),
    Orange("orange", "Voltic Orange"),
    PurpleStock("purple-stock", "Purple");

    val scheme: VolticScheme
        get() = when (this) {
            Purple -> kPurpleScheme
            Orange -> kOrangeScheme
            PurpleStock -> kPurpleStockScheme
        }

    companion object {
        fun fromId(inId: String?): VolticPalette = entries.firstOrNull { it.id == inId } ?: Purple
    }
}

// Usage: MaterialTheme.colorScheme.primary, VolticTheme.extended.warning
val LocalVolticExtended = staticCompositionLocalOf { kPurpleScheme.darkExtended }

object VolticTheme {
    val extended: VolticExtendedColors
        @Composable get() = LocalVolticExtended.current
}

/** Maps the active M3 ColorScheme onto the app's legacy AppColors slots, so the
~50 existing `LocalAppColors.current` call sites theme with the palette:
panel = surface, field = surfaceVariant, border = the subtle outlineVariant,
dim = onSurfaceVariant, accent = primary. */
internal fun appColorsFromScheme(inScheme: ColorScheme): AppColors = AppColors(
    bg = inScheme.background,
    panel = inScheme.surface,
    field = inScheme.surfaceVariant,
    border = inScheme.outlineVariant,
    accent = inScheme.primary,
    text = inScheme.onSurface,
    dim = inScheme.onSurfaceVariant,
    onAccent = inScheme.onPrimary,
)

// ==================
// MARK: Palette definitions (from voltic-pack/theme)
// ==================

private val kPurpleScheme = VolticScheme(
    light = lightColorScheme(
        primary = Color(0xFF7F52FF), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE3D4FF), onPrimaryContainer = Color(0xFF2A1065),
        secondary = Color(0xFF5F5873), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE5DFF3), onSecondaryContainer = Color(0xFF1B1626),
        tertiary = Color(0xFFF5B437), onTertiary = Color(0xFF3F2E00),
        tertiaryContainer = Color(0xFFFFE8B8), onTertiaryContainer = Color(0xFF2F2000),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFAF8FF), onBackground = Color(0xFF1B1626),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B1626),
        surfaceVariant = Color(0xFFECE6F6), onSurfaceVariant = Color(0xFF453D5C),
        outline = Color(0xFF6A5F8A), outlineVariant = Color(0xFFD7CDEB),
        inverseSurface = Color(0xFF2E2840), inverseOnSurface = Color(0xFFF3EFFB),
        inversePrimary = Color(0xFF9D7BFF), surfaceTint = Color(0xFF7F52FF), scrim = Color(0xFF000000),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF9D7BFF), onPrimary = Color(0xFF1F0068),
        primaryContainer = Color(0xFF4A22C4), onPrimaryContainer = Color(0xFFE3D4FF),
        secondary = Color(0xFFC9C1DC), onSecondary = Color(0xFF2E2840),
        secondaryContainer = Color(0xFF453D5C), onSecondaryContainer = Color(0xFFE5DFF3),
        tertiary = Color(0xFFF5B437), onTertiary = Color(0xFF3F2E00),
        tertiaryContainer = Color(0xFF6D5000), onTertiaryContainer = Color(0xFFFFE8B8),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F0D17), onBackground = Color(0xFFE6E1F2),
        surface = Color(0xFF171423), onSurface = Color(0xFFE6E1F2),
        surfaceVariant = Color(0xFF37304A), onSurfaceVariant = Color(0xFFCFC6E4),
        outline = Color(0xFF8F86A8), outlineVariant = Color(0xFF37304A),
        inverseSurface = Color(0xFFE6E1F2), inverseOnSurface = Color(0xFF171423),
        inversePrimary = Color(0xFF7F52FF), surfaceTint = Color(0xFF9D7BFF), scrim = Color(0xFF000000),
    ),
    lightExtended = VolticExtendedColors(
        warning = Color(0xFFB26A00), onWarning = Color(0xFFFFFFFF),
        warningContainer = Color(0xFFFFDF9E), onWarningContainer = Color(0xFF2A1800),
    ),
    darkExtended = VolticExtendedColors(
        warning = Color(0xFFFFC960), onWarning = Color(0xFF3F2E00),
        warningContainer = Color(0xFF5C4300), onWarningContainer = Color(0xFFFFDF9E),
    ),
)

private val kOrangeScheme = VolticScheme(
    light = lightColorScheme(
        primary = Color(0xFFE86A33), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBCB), onPrimaryContainer = Color(0xFF380D00),
        secondary = Color(0xFF9C4E2A), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFE0D2), onSecondaryContainer = Color(0xFF3A0F00),
        tertiary = Color(0xFF8F6A00), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBE08A), onTertiaryContainer = Color(0xFF2D2000),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF8F5), onBackground = Color(0xFF221A15),
        surface = Color(0xFFFFF8F5), onSurface = Color(0xFF221A15),
        surfaceVariant = Color(0xFFF4DED4), onSurfaceVariant = Color(0xFF52443C),
        outline = Color(0xFF85736B), outlineVariant = Color(0xFFD7C2B8),
        inverseSurface = Color(0xFF382E29), inverseOnSurface = Color(0xFFFFEDE5),
        inversePrimary = Color(0xFFFFB598), surfaceTint = Color(0xFFE86A33), scrim = Color(0xFF000000),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB598), onPrimary = Color(0xFF5B1B00),
        primaryContainer = Color(0xFFC1531F), onPrimaryContainer = Color(0xFFFFDBCB),
        secondary = Color(0xFFFFB599), onSecondary = Color(0xFF5E1A00),
        secondaryContainer = Color(0xFF7D3715), onSecondaryContainer = Color(0xFFFFE0D2),
        tertiary = Color(0xFFF5B437), onTertiary = Color(0xFF3F2E00),
        tertiaryContainer = Color(0xFF6D5000), onTertiaryContainer = Color(0xFFFBE08A),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF101B28), onBackground = Color(0xFFE3E7ED),
        surface = Color(0xFF16283A), onSurface = Color(0xFFE3E7ED),
        surfaceVariant = Color(0xFF33414F), onSurfaceVariant = Color(0xFFC9D3DD),
        outline = Color(0xFF7C8A97), outlineVariant = Color(0xFF33414F),
        inverseSurface = Color(0xFFE3E7ED), inverseOnSurface = Color(0xFF16283A),
        inversePrimary = Color(0xFFE86A33), surfaceTint = Color(0xFFFFB598), scrim = Color(0xFF000000),
    ),
    lightExtended = VolticExtendedColors(
        warning = Color(0xFF7D5800), onWarning = Color(0xFFFFFFFF),
        warningContainer = Color(0xFFFFDF9E), onWarningContainer = Color(0xFF271900),
    ),
    darkExtended = VolticExtendedColors(
        warning = Color(0xFFF2C13D), onWarning = Color(0xFF3E2E00),
        warningContainer = Color(0xFF5E4600), onWarningContainer = Color(0xFFFFDF9E),
    ),
)

private val kPurpleStockScheme = VolticScheme(
    light = lightColorScheme(
        primary = Color(0xFF7F52FF), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF22005D),
        secondary = Color(0xFF615B71), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE7DEF8), onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF9A25AE), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD6FE), onTertiaryContainer = Color(0xFF35003F),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFDF7FF), onBackground = Color(0xFF1C1B20),
        surface = Color(0xFFFDF7FF), onSurface = Color(0xFF1C1B20),
        surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454E),
        outline = Color(0xFF7A757F), outlineVariant = Color(0xFFCBC4CF),
        inverseSurface = Color(0xFF313035), inverseOnSurface = Color(0xFFF4EFF4),
        inversePrimary = Color(0xFFCFBCFF), surfaceTint = Color(0xFF7F52FF), scrim = Color(0xFF000000),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF3A0AA5),
        primaryContainer = Color(0xFF6236E0), onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFCBC2DB), onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF494458), onSecondaryContainer = Color(0xFFE7DEF8),
        tertiary = Color(0xFFF9ABFF), onTertiary = Color(0xFF570066),
        tertiaryContainer = Color(0xFF7B0E8F), onTertiaryContainer = Color(0xFFFFD6FE),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF141220), onBackground = Color(0xFFE6E1E9),
        surface = Color(0xFF1D1A2C), onSurface = Color(0xFFE6E1E9),
        surfaceVariant = Color(0xFF3B3548), onSurfaceVariant = Color(0xFFCBC4CF),
        outline = Color(0xFF948F99), outlineVariant = Color(0xFF3B3548),
        inverseSurface = Color(0xFFE6E1E9), inverseOnSurface = Color(0xFF1D1A2C),
        inversePrimary = Color(0xFF7F52FF), surfaceTint = Color(0xFFCFBCFF), scrim = Color(0xFF000000),
    ),
    lightExtended = VolticExtendedColors(
        warning = Color(0xFFB26A00), onWarning = Color(0xFFFFFFFF),
        warningContainer = Color(0xFFFFDF9E), onWarningContainer = Color(0xFF2A1800),
    ),
    darkExtended = VolticExtendedColors(
        warning = Color(0xFFFFC960), onWarning = Color(0xFF3F2E00),
        warningContainer = Color(0xFF5C4300), onWarningContainer = Color(0xFFFFDF9E),
    ),
)
