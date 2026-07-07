package screens
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

@Composable
internal fun LayoutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Layout", "Row / Column / Box — Arrangement and Alignment")

        Section(
            "Row with Modifier.weight(...)",
            "Children split the leftover space in their weight ratio. Here: 1:2:1 across a 400dp row, " +
                "with two unweighted Swatches taking their intrinsic width first.",
        ) {
            Row(
                modifier = Modifier.width(400.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Swatch("A")
                Box(modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primary)) {}
                Box(modifier = Modifier
                    .weight(2f)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.secondary)) {}
                Box(modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primary)) {}
                Swatch("B")
            }
        }

        Section(
            "Column with Modifier.weight(...) fill=false",
            "Same idea vertically. The middle child uses fill=false so it stays at its intrinsic " +
                "height while still claiming its weight share.",
        ) {
            Column(
                modifier = Modifier.height(220.dp).width(120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)) {}
                Box(modifier = Modifier
                    .weight(1f, fill = false)
                    .height(20.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondary)) {}
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)) {}
            }
        }

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
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(120.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Swatch("•") }
            }
        }

    }
}
