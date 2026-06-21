package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.menuAnchor
import androidx.compose.material.rememberMenuAnchor
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TooltipBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DialogsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Dialogs + overlays",
            "Built on the popup host installed by composeWindow — appear above all content.",
        )

        // Dialog
        Section("Dialog / AlertDialog", "Modal: click the scrim to dismiss.") {
            var vShow by remember { mutableStateOf(false) }
            Button(onClick = { vShow = true }) {
                Text("Open dialog", color = MaterialTheme.colors.onPrimary)
            }
            if (vShow) {
                AlertDialog(
                    onDismissRequest = { vShow = false },
                    title = { Text("Confirm action", color = MaterialTheme.colors.onSurface, fontSize = 18.sp) },
                    text = {
                        Text(
                            "Are you sure you want to proceed? This is a non-destructive demonstration.",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                    },
                    confirmButton = {
                        Button(onClick = { vShow = false }) {
                            Text("Confirm", color = MaterialTheme.colors.onPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { vShow = false }) {
                            Text("Cancel", color = MaterialTheme.colors.primary)
                        }
                    },
                )
            }
        }

        // DropdownMenu
        Section("DropdownMenu", "Anchored popup with selectable items. Click outside to dismiss. The anchor's window-coordinate position is tracked via Modifier.menuAnchor.") {
            var vExpanded by remember { mutableStateOf(false) }
            val vAnchor = rememberMenuAnchor()
            var vSelected by remember { mutableStateOf("None") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vExpanded = !vExpanded },
                    modifier = Modifier.menuAnchor(vAnchor),
                ) { Text("Open menu", color = MaterialTheme.colors.primary) }
                DropdownMenu(
                    expanded = vExpanded,
                    onDismissRequest = { vExpanded = false },
                    anchor = vAnchor,
                    offsetY = 4.dp,
                ) {
                    for (vLabel in listOf("Apple", "Banana", "Cherry", "Date")) {
                        DropdownMenuItem(onClick = { vSelected = vLabel; vExpanded = false }) {
                            Text(vLabel, color = MaterialTheme.colors.onSurface, fontSize = 14.sp)
                        }
                    }
                }
                Text("Selected: $vSelected", fontSize = 14.sp)
            }
        }

        // Snackbar
        Section("Snackbar", "Auto-dismissed transient toast. Pinned to bottom-center of the window.") {
            val vHost = remember { SnackbarHostState() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vHost.show("Saved — your changes are live.") }) {
                    Text("Show simple", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = {
                    vHost.show("Couldn't load profile.", actionLabel = "Retry", durationMillis = 6000L)
                }) { Text("With action", color = MaterialTheme.colors.onPrimary) }
            }
            SnackbarHost(hostState = vHost) {}
        }

        // Tooltip
        Section("TooltipBox", "Hover the target — text appears below it after a 600 ms delay.") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TooltipBox(text = "Save the document") {
                    Button(onClick = {}) { Text("Save", color = MaterialTheme.colors.onPrimary) }
                }
                TooltipBox(text = "Discard changes (cannot be undone).") {
                    OutlinedButton(onClick = {}) { Text("Discard", color = MaterialTheme.colors.primary) }
                }
            }
        }
    }
}
