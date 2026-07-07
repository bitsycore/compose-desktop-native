package screens
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@Composable
internal fun ButtonsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Buttons",
            "Filled / Outlined / Text variants with shape, content padding, and enabled states.",
        )

        Section("Filled Button", "Default: RoundedCornerShape(4.dp), Material primary container") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Action", color = MaterialTheme.colorScheme.onPrimary) }
                Button(onClick = {}, enabled = false) {
                    Text("Disabled", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Section("OutlinedButton", "Transparent fill, 1.dp border, primary content colour") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}) {
                    Text("Outlined", color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Disabled", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Section("TextButton", "No background, no border — text-only affordance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {}) {
                    Text("Text", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Section("Shape variants", "shape = RoundedCornerShape(0/12.dp) and CircleShape with contentPadding 0") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}, shape = RoundedCornerShape(0.dp)) {
                    Text("Rect", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                    Text("12dp", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
