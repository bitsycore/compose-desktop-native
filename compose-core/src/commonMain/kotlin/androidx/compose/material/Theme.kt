package androidx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ==================
// MARK: Colors
// ==================

data class Colors(
    val primary: Color = Color(0xFF6200EE),
    val primaryVariant: Color = Color(0xFF3700B3),
    val secondary: Color = Color(0xFF03DAC6),
    val secondaryVariant: Color = Color(0xFF018786),
    val background: Color = Color(0xFF121212),
    val surface: Color = Color(0xFF1E1E1E),
    val error: Color = Color(0xFFCF6679),
    val onPrimary: Color = Color.White,
    val onSecondary: Color = Color.Black,
    val onBackground: Color = Color.White,
    val onSurface: Color = Color.White,
    val onError: Color = Color.Black,
)

fun lightColors(
    primary: Color = Color(0xFF6200EE),
    background: Color = Color(0xFFFAFAFA),
    surface: Color = Color.White,
    onPrimary: Color = Color.White,
    onBackground: Color = Color.Black,
    onSurface: Color = Color.Black,
) = Colors(
    primary = primary,
    background = background,
    surface = surface,
    onPrimary = onPrimary,
    onBackground = onBackground,
    onSurface = onSurface,
)

fun darkColors(
    primary: Color = Color(0xFFBB86FC),
    background: Color = Color(0xFF121212),
    surface: Color = Color(0xFF1E1E1E),
    onPrimary: Color = Color.Black,
    onBackground: Color = Color.White,
    onSurface: Color = Color.White,
) = Colors(
    primary = primary,
    background = background,
    surface = surface,
    onPrimary = onPrimary,
    onBackground = onBackground,
    onSurface = onSurface,
)

// ==================
// MARK: MaterialTheme
// ==================

val LocalColors = compositionLocalOf { Colors() }

object MaterialTheme {
    val colors: Colors
        @Composable get() = LocalColors.current
}

@Composable
fun MaterialTheme(
    colors: Colors = Colors(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalColors provides colors) {
        content()
    }
}
