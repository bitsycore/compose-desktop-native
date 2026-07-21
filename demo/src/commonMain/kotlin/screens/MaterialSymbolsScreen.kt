package screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined
import com.compose.sdl.icons.material.symbols.MaterialSymbolsRounded
import com.compose.sdl.icons.material.symbols.MaterialSymbolsSharp

// ==================
// MARK: Material Symbols screen — variable-font icon engine showcase
// ==================

/** The :material-symbols engine demos: the four variable-font axes (FILL /
   wght / GRAD / opsz), the animated fill transition, and the three style
   families. Runs the SAME shared code on native (IconFont over SDL3_ttf-fork
   / Skia) and JVM (Skiko direct) — differences between the builds = port
   bugs. */
@Composable
internal fun MaterialSymbolsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Material Symbols",
            "MaterialSymbolsOutlined / Rounded / Sharp — drop the dependency, call the composable, " +
                "font auto-installs. Four variable-font axes as direct parameters.",
        )

        // ============
        //  Variable-font axes — each row pins everything except one axis.
        //  Skia, the SDL3_ttf fork (TTF_SetFontAxisValue) and the JVM build
        //  (Skiko Typeface.makeClone) all honour the axes.

        Section(
            "Weight (wght 100..700)",
            "Most-used axis. 400 = regular, 700 = bold.",
        ) {
            val vWeights = listOf(100, 200, 300, 400, 500, 600, 700)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (vWeight in vWeights) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MaterialSymbolsOutlined(
                            MaterialSymbols.Home,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 32.dp,
                            weight = vWeight,
                        )
                        Text(vWeight.toString(), fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Section(
            "Fill (FILL 0..1)",
            "Outlined → filled. Continuous range; values between 0 and 1 morph the glyph.",
        ) {
            val vFills = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (vFill in vFills) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MaterialSymbolsOutlined(
                            MaterialSymbols.Favorite,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 32.dp,
                            fill = vFill,
                        )
                        Text(vFill.toString(), fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // ============
        //  Animated fill — the modern M3 selected-state icon transition:
        //  the FILL axis (and tint) animate on click instead of swapping
        //  between two static icons.
        Section(
            "Animated fill",
            "Click an icon: animateFloatAsState drives the FILL axis (plus tint) — " +
                "the Material 3 selected-state transition, morphing the glyph instead of swapping it.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AnimatedFillIcon(MaterialSymbols.Favorite, "Favorite")
                AnimatedFillIcon(MaterialSymbols.Star, "Star")
                AnimatedFillIcon(MaterialSymbols.Bookmark, "Bookmark")
                AnimatedFillIcon(MaterialSymbols.Notifications, "Notifications")
            }
        }

        Section(
            "Grade (GRAD -25..200)",
            "Fine weight nudge without changing the glyph footprint. Negative = thinner, positive = heavier.",
        ) {
            val vGrades = listOf(-25, 0, 50, 100, 200)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (vGrade in vGrades) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MaterialSymbolsOutlined(
                            MaterialSymbols.Star,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 32.dp,
                            grade = vGrade,
                            fill = 1f,
                        )
                        Text(vGrade.toString(), fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Section(
            "Optical size (opsz 20..48)",
            "Picks the design size the glyph was drawn for; rendered display size is unchanged.",
        ) {
            val vSizes = listOf(20, 24, 32, 40, 48)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (vSize in vSizes) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MaterialSymbolsOutlined(
                            MaterialSymbols.Settings,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 32.dp,
                            opticalSize = vSize,
                        )
                        Text(vSize.toString(), fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Section(
            "Combined axes",
            "All four axes set together — typical real-world use.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MaterialSymbolsOutlined(MaterialSymbols.Favorite,
                        tint = MaterialTheme.colorScheme.primary, size = 40.dp)
                    Text("Default", fontSize = 10.sp,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MaterialSymbolsOutlined(MaterialSymbols.Favorite,
                        tint = MaterialTheme.colorScheme.primary, size = 40.dp,
                        fill = 1f, weight = 700)
                    Text("fill=1 wght=700", fontSize = 10.sp,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MaterialSymbolsOutlined(MaterialSymbols.Favorite,
                        tint = MaterialTheme.colorScheme.primary, size = 40.dp,
                        fill = 1f, weight = 100)
                    Text("fill=1 wght=100", fontSize = 10.sp,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MaterialSymbolsOutlined(MaterialSymbols.Favorite,
                        tint = MaterialTheme.colorScheme.primary, size = 40.dp,
                        fill = 1f, weight = 500, grade = 200, opticalSize = 40)
                    Text("fill=1 wght=500 GRAD=200 opsz=40", fontSize = 10.sp,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }

        // ============
        //  Style families.

        val vNames = listOf(
            "Home" to MaterialSymbols.Home,
            "Search" to MaterialSymbols.Search,
            "Settings" to MaterialSymbols.Settings,
            "Favorite" to MaterialSymbols.Favorite,
            "Person" to MaterialSymbols.Person,
            "Add" to MaterialSymbols.Add,
            "Delete" to MaterialSymbols.Delete,
            "Edit" to MaterialSymbols.Edit,
            "Save" to MaterialSymbols.Save,
            "Refresh" to MaterialSymbols.Refresh,
            "Download" to MaterialSymbols.Download,
            "Upload" to MaterialSymbols.Upload,
        )

        // Each section calls a different style entry point; the codepoint
        // constants in core are shared.
        @Composable
        fun iconRow(styleIcon: @Composable (Int) -> Unit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (vRow in vNames.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        for ((vLabel, vCp) in vRow) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                styleIcon(vCp)
                                Text(vLabel, fontSize = 11.sp,
                                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        Section("MaterialSymbolsOutlined", "Family: \"${MaterialSymbolsOutlined.Family}\".") {
            iconRow { cp -> MaterialSymbolsOutlined(cp, tint = MaterialTheme.colorScheme.primary) }
        }
        Section("MaterialSymbolsRounded", "Family: \"${MaterialSymbolsRounded.Family}\".") {
            iconRow { cp -> MaterialSymbolsRounded(cp, tint = MaterialTheme.colorScheme.primary) }
        }
        Section("MaterialSymbolsSharp", "Family: \"${MaterialSymbolsSharp.Family}\".") {
            iconRow { cp -> MaterialSymbolsSharp(cp, tint = MaterialTheme.colorScheme.primary) }
        }

        Section(
            "Side-by-side comparison",
            "Same icon across all three styles.",
        ) {
            val vCompareNames = listOf(
                "Home" to MaterialSymbols.Home,
                "Star" to MaterialSymbols.Star,
                "Favorite" to MaterialSymbols.Favorite,
                "Settings" to MaterialSymbols.Settings,
                "Delete" to MaterialSymbols.Delete,
                "Notifications" to MaterialSymbols.Notifications,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Spacer(modifier = Modifier.width(80.dp))
                    for (vLabel in listOf("Outlined", "Rounded", "Sharp")) {
                        Text(vLabel, fontSize = 12.sp,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                             modifier = Modifier.width(48.dp))
                    }
                }
                for ((vName, vCp) in vCompareNames) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(vName, fontSize = 13.sp,
                             color = MaterialTheme.colorScheme.onSurface,
                             modifier = Modifier.width(80.dp))
                        MaterialSymbolsOutlined(vCp, tint = MaterialTheme.colorScheme.primary)
                        MaterialSymbolsRounded(vCp, tint = MaterialTheme.colorScheme.primary)
                        MaterialSymbolsSharp(vCp, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** One click-toggled icon: FILL 0↔1 and tint animate together — swap-free
   selected-state feedback, the Material 3 expressive icon pattern. */
@Composable
private fun AnimatedFillIcon(codepoint: Int, label: String) {
    var vSelected by remember { mutableStateOf(false) }
    val vFill by animateFloatAsState(
        targetValue = if (vSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "fill",
    )
    val vTint by animateColorAsState(
        targetValue = if (vSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300),
        label = "tint",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { vSelected = !vSelected }) {
            MaterialSymbolsOutlined(codepoint, size = 28.dp, tint = vTint, fill = vFill)
        }
        Text(label, fontSize = 11.sp,
             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
