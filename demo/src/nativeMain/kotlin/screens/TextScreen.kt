package screens

import ScreenTitle
import Section
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@Composable
internal fun TextScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Text", "fontSize / color / textAlign / softWrap")

        Section("Font sizes", "All Sp-typed, scale with theme") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default text", color = MaterialTheme.colors.onSurface)
                Text("fontSize 12.sp", fontSize = 12.sp, color = MaterialTheme.colors.onSurface)
                Text("fontSize 24.sp", fontSize = 24.sp, color = MaterialTheme.colors.onSurface)
                Text("fontSize 32.sp", fontSize = 32.sp, color = MaterialTheme.colors.onSurface)
            }
        }

        Section("Colors") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Color.Red", color = Color.Red, fontSize = 16.sp)
                Text("MaterialTheme.colors.primary", color = MaterialTheme.colors.primary, fontSize = 16.sp)
                Text("MaterialTheme.colors.secondary", color = MaterialTheme.colors.secondary, fontSize = 16.sp)
            }
        }

        Section("TextAlign over fillMaxWidth()") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(400.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                        color = MaterialTheme.colors.onBackground)
                    Text("Center", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground)
                    Text("End", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        color = MaterialTheme.colors.onBackground)
                }
            }
        }

        Section("Soft-wrap", "Long text auto-wraps at word boundaries inside a constrained width") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump!",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Section("softWrap = false", "Overflows horizontally — Surface clips, content cropped at the right edge") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "This long sentence does not wrap and will get clipped.",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
