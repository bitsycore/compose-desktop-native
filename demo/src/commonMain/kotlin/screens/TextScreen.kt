package screens
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@Composable
internal fun TextScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Text", "fontSize / color / textAlign / softWrap")

        Section("Font sizes", "All TextUnit-typed, scale with theme") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default text", color = MaterialTheme.colorScheme.onSurface)
                Text("fontSize 12.sp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("fontSize 24.sp", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("fontSize 32.sp", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Section("Font weights", "fontWeight drives the variable font's wght axis (Thin → Black)") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Thin 100 — the quick brown fox", fontWeight = androidx.compose.ui.text.font.FontWeight.Thin, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Normal 400 — the quick brown fox", fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Medium 500 — the quick brown fox", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Bold 700 — the quick brown fox", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Black 900 — the quick brown fox", fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Section("Colors") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Color.Red", color = Color.Red, fontSize = 16.sp)
                Text("MaterialTheme.colorScheme.primary", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                Text("MaterialTheme.colorScheme.secondary", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp)
            }
        }

        Section("TextAlign over fillMaxWidth()") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(400.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Center", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("End", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Section("Soft-wrap", "Long text auto-wraps at word boundaries inside a constrained width") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump!",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Section("softWrap = false", "Overflows horizontally — Surface clips, content cropped at the right edge") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "This long sentence does not wrap and will get clipped.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
