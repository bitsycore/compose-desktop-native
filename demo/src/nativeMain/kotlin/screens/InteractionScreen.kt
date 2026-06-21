package screens

import ScreenTitle
import Section
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*

// ==================
// MARK: Interaction (hover / press / focus) screen
// ==================

@Composable
internal fun InteractionScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Interaction", "Hover, press, focus state layers")

        Section("Hover overlay", "Move the cursor over each variant") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.onPrimary) }
                OutlinedButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
                TextButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
            }
        }

        Section("Press / drag-off", "Hold the mouse down; deeper overlay shows. Drag out and the press cancels.") {
            Button(onClick = {}) { Text("Press and hold", color = MaterialTheme.colors.onPrimary) }
        }

        Section("TextField focus", "Border color + width transition on focus / blur") {
            var a by remember { mutableStateOf("") }
            var b by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = a, onValueChange = { a = it }, label = "First", modifier = Modifier.width(160.dp))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = "Second", modifier = Modifier.width(160.dp))
            }
        }
    }
}
