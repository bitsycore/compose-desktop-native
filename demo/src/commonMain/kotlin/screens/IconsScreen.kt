package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined
import com.compose.sdl.icons.material.symbols.MaterialSymbolsRounded
import com.compose.sdl.icons.material.symbols.MaterialSymbolsSharp
import demo.generated.resources.Res
import demo.generated.resources.heart
import org.jetbrains.compose.resources.painterResource

// ==================
// MARK: Icons screen — material3 icon APIs
// ==================

/** material3 icon APIs: Icon(painter) over compose resources and IconButton. */
@Composable
internal fun IconsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Icons",
            "material3 Icon(painter) over compose resources + IconButton.",
        )

        Section("Painter-based Icon", "Reads from composeResources/drawable/*.xml (Android vector) or *.svg.") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painter = painterResource(Res.drawable.heart), contentDescription = "Heart")
                Text("painterResource(Res.drawable.heart)", fontSize = 13.sp)
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
