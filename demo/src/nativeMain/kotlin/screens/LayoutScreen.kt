package screens

import ScreenTitle
import Section
import Swatch
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

@Composable
internal fun LayoutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Layout", "Row / Column / Box — Arrangement and Alignment")

        Section("Row with spacedBy(16.dp)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Swatch("A"); Swatch("B"); Swatch("C")
            }
        }

        Section("Row with Arrangement.SpaceBetween", "Children pinned to start and end, evenly spaced") {
            Row(
                modifier = Modifier.width(400.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Swatch("L"); Swatch("M"); Swatch("R")
            }
        }

        Section("Column with spacedBy(8.dp) + CenterHorizontally") {
            Column(
                modifier = Modifier.width(120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Swatch("1"); Swatch("2"); Swatch("3")
            }
        }

        Section("Box with contentAlignment.Center") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.size(120.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Swatch("•") }
            }
        }
    }
}
