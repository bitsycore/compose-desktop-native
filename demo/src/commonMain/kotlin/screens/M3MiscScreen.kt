@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Label
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollField
import androidx.compose.material3.ScrollFieldDefaults
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberScrollFieldState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon

// Material3 — the remaining odds and ends: Scaffold, Snackbar, secure fields,
// exposed dropdown, segmented list, sliders extra, expressive theme.
@Composable
internal fun M3MiscScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("M3 misc", "Scaffold, Snackbar, secure fields, exposed dropdown, segmented list, more sliders.")

		Section("Scaffold", "topBar + FAB + content slots, in a fixed-height frame") {
			Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
				Scaffold(
					topBar = { TopAppBar(title = { Text("Scaffold demo") }) },
					floatingActionButton = {
						FloatingActionButton(onClick = {}) { DemoIcon(DemoIcon.Add) }
					},
				) { vPadding ->
					Box(modifier = Modifier.fillMaxSize().padding(vPadding), contentAlignment = Alignment.Center) {
						Text("Scaffold body")
					}
				}
			}
		}

		Section("Snackbar", "The standalone composable (SnackbarHost drives the real queue)") {
			Snackbar(
				action = { TextButton(onClick = {}) { Text("Undo") } },
			) { Text("Item deleted") }
		}

		Section("BasicAlertDialog", "Undecorated dialog shell — bring your own Surface") {
			var vOpen by remember { mutableStateOf(false) }
			Button(onClick = { vOpen = true }) { Text("Open basic dialog") }
			if (vOpen) {
				BasicAlertDialog(onDismissRequest = { vOpen = false }) {
					Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
						Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
							Text("A bare dialog surface.")
							TextButton(onClick = { vOpen = false }) { Text("Close") }
						}
					}
				}
			}
		}

		Section("SecureTextField / OutlinedSecureTextField", "Password entry with masked glyphs") {
			SecureTextField(state = rememberTextFieldState(), label = { Text("Password") })
			OutlinedSecureTextField(state = rememberTextFieldState(), label = { Text("Outlined password") })
		}

		Section("ExposedDropdownMenuBox", "The combo-box pattern: field + attached menu") {
			val vOptions = listOf("Kotlin", "Swift", "Rust")
			var vExpanded by remember { mutableStateOf(false) }
			var vChoice by remember { mutableStateOf(vOptions[0]) }
			ExposedDropdownMenuBox(expanded = vExpanded, onExpandedChange = { vExpanded = it }) {
				OutlinedTextField(
					value = vChoice,
					onValueChange = {},
					readOnly = true,
					trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vExpanded) },
					modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
				)
				ExposedDropdownMenu(expanded = vExpanded, onDismissRequest = { vExpanded = false }) {
					for (vOption in vOptions) {
						DropdownMenuItem(
							text = { Text(vOption) },
							onClick = { vChoice = vOption; vExpanded = false },
						)
					}
				}
			}
		}

		Section("DropdownMenuGroup", "Groups related menu items with shared shapes") {
			DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
				DropdownMenuItem(text = { Text("Cut") }, onClick = {})
				DropdownMenuItem(text = { Text("Copy") }, onClick = {})
				DropdownMenuItem(text = { Text("Paste") }, onClick = {})
			}
		}

		Section("DropdownMenuPopup", "The raw popup shell DropdownMenu builds on") {
			var vOpen by remember { mutableStateOf(false) }
			Box {
				Button(onClick = { vOpen = true }) { Text("Open raw menu popup") }
				DropdownMenuPopup(expanded = vOpen, onDismissRequest = { vOpen = false }) {
					DropdownMenuItem(text = { Text("Item A") }, onClick = { vOpen = false })
					DropdownMenuItem(text = { Text("Item B") }, onClick = { vOpen = false })
				}
			}
		}

		Section("SegmentedListItem", "List items with segmented (grouped) corner shapes") {
			val vItems = listOf("First", "Middle", "Last")
			Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
				vItems.forEachIndexed { vIdx, vLabel ->
					SegmentedListItem(
						onClick = {},
						shapes = ListItemDefaults.segmentedShapes(vIdx, vItems.size),
					) { Text(vLabel) }
				}
			}
		}

		Section("RangeSlider / VerticalSlider", "Two-thumb range + vertical orientation") {
			var vRange by remember { mutableStateOf(0.2f..0.7f) }
			RangeSlider(value = vRange, onValueChange = { vRange = it })
			Box(modifier = Modifier.height(160.dp)) {
				VerticalSlider(state = rememberSliderState(value = 0.4f))
			}
		}

		Section("Label", "Attaches a small label to draggable/hoverable content") {
			Label(
				label = {
					Surface(shape = MaterialTheme.shapes.small, tonalElevation = 4.dp) {
						Text("42%", modifier = Modifier.padding(6.dp))
					}
				},
				isPersistent = true,
			) {
				Button(onClick = {}) { Text("Labelled anchor") }
			}
		}

		Section("ScrollField", "Vertically-snapping value field (drag it)") {
			// ScrollField is a VerticalPager inside — it MUST get a bounded
			// height (the screen's outer scroll column measures children with
			// infinite max height, which scrollables reject).
			Box(modifier = Modifier.width(120.dp).height(ScrollFieldDefaults.ScrollFieldHeight)) {
				ScrollField(state = rememberScrollFieldState(itemCount = 10, index = 3))
			}
		}

		Section("MaterialExpressiveTheme", "Subtree themed with the expressive defaults") {
			MaterialExpressiveTheme {
				ProvideTextStyle(MaterialTheme.typography.titleMedium) {
					Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
						Button(onClick = {}) { Text("Expressive button") }
						Text("ProvideTextStyle applies titleMedium here")
					}
				}
			}
		}
	}
}
