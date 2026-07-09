package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.sdl.icons.material.symbols.rounded.MaterialSymbolsRounded
import com.compose.sdl.icons.material.symbols.sharp.MaterialSymbolsSharp
import demo.generated.resources.Res
import demo.generated.resources.heart
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun IconsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Icons",
            "MaterialSymbolsOutlined / Rounded / Sharp — drop the dependency, call the composable, font auto-installs.",
        )

        // ============
        //  Variable-font axes — the priority demo. The four axes are direct
        //  parameters on the icon composable, so each row pins everything
        //  except one. Skia honours every axis; SDL3_ttf 3.2 ignores them.

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
        //  Style families + drawable icons.

        Section("Painter-based Icon", "Reads from composeResources/drawable/*.xml (Android vector) or *.svg.") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painter = painterResource(Res.drawable.heart), contentDescription = "Heart")
                Text("painterResource(Res.drawable.heart)", fontSize = 13.sp)
            }
        }

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

        Section("IconButton", "40dp clickable circle. Hover overlay matches Material 1.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {}) {
                    MaterialSymbolsOutlined(MaterialSymbols.Favorite,
                        tint = MaterialTheme.colorScheme.primary, fill = 1f, weight = 600)
                }
                IconButton(onClick = {}) {
                    MaterialSymbolsRounded(MaterialSymbols.Share,
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = {}) {
                    MaterialSymbolsSharp(MaterialSymbols.MoreVert,
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
