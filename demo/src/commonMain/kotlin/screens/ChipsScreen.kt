package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

// Material3 — Assist / Suggestion / Filter / Input chips.
@Composable
internal fun ChipsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Chips", "material3 Assist / Suggestion / Filter / Input chips.")

		Section("AssistChip / SuggestionChip", "Action + suggestion affordances") {
			Row(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				AssistChip(onClick = {}, label = { Text("Assist") })
				AssistChip(onClick = {}, enabled = false, label = { Text("Disabled") })
				SuggestionChip(onClick = {}, label = { Text("Suggestion") })
			}
		}

		Section("FilterChip", "Selectable — toggles a tonal fill + checkmark") {
			var vA by remember { mutableStateOf(true) }
			var vB by remember { mutableStateOf(false) }
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				FilterChip(selected = vA, onClick = { vA = !vA }, label = { Text("Enabled") })
				FilterChip(selected = vB, onClick = { vB = !vB }, label = { Text("Toggle me") })
			}
		}

		Section("Elevated chips", "Shadow-lifted variants of Assist / Filter / Suggestion") {
			var vSel by remember { mutableStateOf(true) }
			Row(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				ElevatedAssistChip(onClick = {}, label = { Text("Assist") })
				ElevatedFilterChip(selected = vSel, onClick = { vSel = !vSel }, label = { Text("Filter") })
				ElevatedSuggestionChip(onClick = {}, label = { Text("Suggestion") })
			}
		}

		Section("InputChip", "A discrete entry — click to remove") {
			var vChips by remember { mutableStateOf(listOf("Kotlin", "Native", "SDL3", "Compose")) }
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				for (vChip in vChips) {
					InputChip(selected = false, onClick = { vChips = vChips - vChip }, label = { Text(vChip) })
				}
				if (vChips.isEmpty()) {
					AssistChip(onClick = { vChips = listOf("Kotlin", "Native", "SDL3", "Compose") }, label = { Text("Reset") })
				}
			}
		}
	}
}
