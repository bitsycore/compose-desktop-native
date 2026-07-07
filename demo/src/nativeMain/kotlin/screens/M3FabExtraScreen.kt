@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon

// Material3 — FAB size/extended variants, the FAB menu, and floating toolbars.
@Composable
internal fun M3FabExtraScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("FAB extra", "Medium/Large FABs, extended-FAB sizes, FAB menu, floating toolbars.")

		Section("Medium / Large FAB", "Size steps above the standard FAB") {
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
				MediumFloatingActionButton(onClick = {}) { DemoIcon(DemoIcon.Add) }
				LargeFloatingActionButton(onClick = {}) { DemoIcon(DemoIcon.Add, size = 32.dp) }
			}
		}

		Section("Extended FAB sizes", "Small / Medium / Large extended FABs") {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				SmallExtendedFloatingActionButton(onClick = {}) { Text("Small extended") }
				MediumExtendedFloatingActionButton(onClick = {}) { Text("Medium extended") }
				LargeExtendedFloatingActionButton(onClick = {}) { Text("Large extended") }
			}
		}

		Section("FloatingActionButtonMenu", "ToggleFloatingActionButton fans out menu items") {
			var vExpanded by remember { mutableStateOf(false) }
			Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.BottomEnd) {
				FloatingActionButtonMenu(
					expanded = vExpanded,
					button = {
						ToggleFloatingActionButton(
							checked = vExpanded,
							onCheckedChange = { vExpanded = it },
						) {
							DemoIcon(if (vExpanded) DemoIcon.Close else DemoIcon.Add)
						}
					},
				) {
					FloatingActionButtonMenuItem(
						onClick = { vExpanded = false },
						text = { Text("Compose") },
						icon = { DemoIcon(DemoIcon.Edit) },
					)
					FloatingActionButtonMenuItem(
						onClick = { vExpanded = false },
						text = { Text("Photo") },
						icon = { DemoIcon(DemoIcon.Image) },
					)
				}
			}
		}

		Section("Floating toolbars", "Horizontal / vertical expanding action strips") {
			var vExpanded by remember { mutableStateOf(true) }
			Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
				HorizontalFloatingToolbar(expanded = vExpanded) {
					IconButton(onClick = { vExpanded = !vExpanded }) { DemoIcon(DemoIcon.Edit) }
					IconButton(onClick = {}) { DemoIcon(DemoIcon.Delete) }
					IconButton(onClick = {}) { DemoIcon(DemoIcon.Share) }
				}
				VerticalFloatingToolbar(expanded = vExpanded) {
					IconButton(onClick = {}) { DemoIcon(DemoIcon.Add) }
					IconButton(onClick = {}) { DemoIcon(DemoIcon.Check) }
				}
			}
		}
	}
}
