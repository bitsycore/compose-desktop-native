package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ModifiersScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Modifiers", "background, border, clip, padding, size, offset, defaultMinSize, fillMaxWidth, color alpha")

        Section("background + border + padding") {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
            ) {
                Text("Content", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Section("clip(RoundedCornerShape(12.dp))", "Clips children only - the background here already follows the shape") {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 60.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("clipped", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Section("offset(x = 20.dp, y = 10.dp)", "Visual nudge only - doesn't change measured size or sibling layout") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("A")
                Box(modifier = Modifier.offset(x = 20.dp, y = 10.dp)) { Swatch("B") }
                Swatch("C")
            }
        }

        Section("defaultMinSize vs size", "defaultMinSize only kicks in when the incoming min is 0") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 120.dp, minHeight = 40.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("min 120 × 40", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("40", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
            }
        }

        Section("padding overloads", "symmetric / per-axis / per-side - padding insets the content") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(16.dp),
                ) { Text("padding(16.dp)", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 28.dp, vertical = 6.dp),
                ) { Text("padding(horizontal = 28, vertical = 6)", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(start = 28.dp, top = 4.dp, end = 4.dp, bottom = 16.dp),
                ) { Text("padding(start = 28, top = 4, end = 4, bottom = 16)", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
            }
        }

        Section("fillMaxWidth()", "Stretches to the parent's width (here, the Card's content width)") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("fillMaxWidth()", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp) }
        }

        Section(
            "Opacity via Color alpha",
            "There's no Modifier.alpha() - fade by lowering the colour's alpha channel (Color.copy(alpha = …)). " +
                "The label keeps full alpha, showing it's per-colour, not a node-wide fade.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                for (vA in listOf(1f, 0.6f, 0.3f)) {
                    Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = vA), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("α $vA", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
                }
            }
        }

        Section(
            "Modifier.alpha - node-wide opacity",
            "Fades the whole subtree as one layer (background, border, AND text together) - contrast with the per-colour fade above, where only the fill faded.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (vA in listOf(1f, 0.6f, 0.3f)) {
                    Box(
                        modifier = Modifier
                            .alpha(vA)
                            .size(width = 96.dp, height = 56.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("alpha $vA", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
                }
            }
        }
    }
}
