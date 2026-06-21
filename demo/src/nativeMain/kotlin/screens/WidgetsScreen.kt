package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.Chip
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.FilterChip
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.ToggleableState
import androidx.compose.material.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WidgetsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Widgets",
            "Selection + progress controls from androidx.compose.material.",
        )

        // Checkbox
        Section("Checkbox", "Binary on/off with optional tri-state for ‘select all’ parents.") {
            var vChecked by remember { mutableStateOf(true) }
            var vTri by remember { mutableStateOf(ToggleableState.Indeterminate) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vChecked, onCheckedChange = { vChecked = it })
                Text(if (vChecked) "On" else "Off", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(24.dp))
                Checkbox(checked = true, onCheckedChange = null, enabled = false)
                Text("Disabled", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(24.dp))
                TriStateCheckbox(state = vTri, onClick = {
                    vTri = when (vTri) {
                        ToggleableState.Off           -> ToggleableState.On
                        ToggleableState.On            -> ToggleableState.Indeterminate
                        ToggleableState.Indeterminate -> ToggleableState.Off
                    }
                })
                Text("Tri-state: $vTri", fontSize = 14.sp)
            }
        }

        // Switch
        Section("Switch", "Pill-shaped toggle. Thumb snaps left/right (no animation in this subset).") {
            var vOn by remember { mutableStateOf(true) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vOn, onCheckedChange = { vOn = it })
                Text(if (vOn) "Enabled" else "Disabled", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(24.dp))
                Switch(checked = false, onCheckedChange = null, enabled = false)
                Text("Disabled control", fontSize = 14.sp)
            }
        }

        // RadioButton
        Section("RadioButton", "Mutually-exclusive selection. Use SegmentedButton if you want a button-row look.") {
            var vSel by remember { mutableStateOf(0) }
            val vOptions = listOf("Low", "Medium", "High")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((vIdx, vLabel) in vOptions.withIndex()) {
                    RadioButton(selected = vSel == vIdx, onClick = { vSel = vIdx })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(vLabel, fontSize = 14.sp)
                    if (vIdx < vOptions.lastIndex) Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }

        // Slider
        Section("Slider", "Continuous + stepped variants. Drag the thumb.") {
            var vValue by remember { mutableStateOf(0.5f) }
            var vSteps by remember { mutableStateOf(2f) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(value = vValue, onValueChange = { vValue = it })
                Text("Continuous: ${(vValue * 100).toInt()}%", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Slider(value = vSteps, onValueChange = { vSteps = it }, valueRange = 0f..5f, steps = 4)
                Text("Stepped 0..5 (4 stops): ${vSteps.toInt()}", fontSize = 14.sp)
            }
        }

        // Progress
        Section("ProgressIndicator", "Linear + Circular (determinate). No animation runtime → indeterminate is static.") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = 0.3f)
                LinearProgressIndicator(progress = 0.7f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(progress = 0.25f)
                    CircularProgressIndicator(progress = 0.5f)
                    CircularProgressIndicator(progress = 0.85f)
                }
            }
        }

        // Divider
        Section("Divider", "Thin horizontal rule.") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Above the line", fontSize = 14.sp)
                Divider()
                Text("Below the line", fontSize = 14.sp)
            }
        }

        // Chip
        Section("Chip / FilterChip", "Outlined pill for filters or quick actions.") {
            var vChip by remember { mutableStateOf(setOf("Apple", "Berry")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(onClick = {}) { Text("Action", color = MaterialTheme.colors.onSurface, fontSize = 13.sp) }
                for (vLabel in listOf("Apple", "Berry", "Cherry")) {
                    FilterChip(
                        selected = vLabel in vChip,
                        onClick = {
                            vChip = if (vLabel in vChip) vChip - vLabel else vChip + vLabel
                        },
                    ) { Text(vLabel, color = MaterialTheme.colors.onSurface, fontSize = 13.sp) }
                }
            }
        }
    }
}
