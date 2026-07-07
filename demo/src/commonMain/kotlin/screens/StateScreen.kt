package screens

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*

// ==================
// MARK: State / Remember screen
// ==================

@Composable
internal fun StateScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("State / Remember", "mutableStateOf, derived state, remember(key) re-init")

        Section("Basic counter", "var n by remember { mutableStateOf(0) }") {
            var n by remember { mutableStateOf(0) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { n++ }) { Text("Increment", color = MaterialTheme.colorScheme.onPrimary) }
                Text("n = $n", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            }
        }

        Section("List state", "remembered List<String>; reassign to add / drop") {
            var items by remember { mutableStateOf(listOf("alpha", "beta", "gamma")) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { items = items + "item ${items.size}" }) {
                        Text("Add", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    OutlinedButton(onClick = { if (items.isNotEmpty()) items = items.dropLast(1) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in items) Text("• $i", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
            }
        }

        Section("Derived state", "Total recomputes on every recomposition that reads aText / bText") {
            var aText by remember { mutableStateOf("3") }
            var bText by remember { mutableStateOf("4") }
            val total = (aText.toIntOrNull() ?: 0) + (bText.toIntOrNull() ?: 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = aText, onValueChange = { aText = it }, modifier = Modifier.width(80.dp))
                Text("+", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
                OutlinedTextField(value = bText, onValueChange = { bText = it }, modifier = Modifier.width(80.dp))
                Text("=", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
                Text(total.toString(), color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            }
        }

        Section("remember(key)", "Toggling the key invalidates the memo so the lambda runs again") {
            var key by remember { mutableStateOf(0) }
            val rolledOnce = remember(key) { (0..99).random() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { key++ }) { Text("Re-roll", color = MaterialTheme.colorScheme.onPrimary) }
                Text("rolled = $rolledOnce", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            }
        }
    }
}
