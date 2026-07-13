package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: Colors screen — Material palette swatches
// ==================

@Preview
@Composable
internal fun ColorsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Colors", "MaterialTheme.colors palette swatches")
        Section("Theme palette") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = MaterialTheme.colorScheme
                ColorRow("primary",             c.primary,          c.onPrimary)
                ColorRow("primaryContainer",    c.primaryContainer, c.onPrimaryContainer)
                ColorRow("secondary",           c.secondary,        c.onSecondary)
                ColorRow("background",          c.background,       c.onBackground)
                ColorRow("surface",             c.surface,          c.onSurface)
                ColorRow("error",               c.error,            c.onError)
            }
        }
    }
}

@Composable
private fun ColorRow(name: String, fill: Color, content: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 180.dp, height = 36.dp)
                .background(fill, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, color = content, fontSize = 14.sp)
        }
    }
}
