@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ElevatedToggleButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon

// Material3 — ButtonGroup, SplitButton, toggle buttons, icon-toggle family, multi-choice segments.
@Composable
internal fun M3ButtonsExtraScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Buttons extra", "ButtonGroup, SplitButton, ToggleButton variants, icon toggles.")

		Section("ButtonGroup", "DSL strip — items overflow into a menu when space runs out") {
			var vStar by remember { mutableStateOf(false) }
			ButtonGroup(
				overflowIndicator = { vMenuState ->
					FilledTonalIconButton(onClick = { if (vMenuState.isShowing) vMenuState.dismiss() else vMenuState.show() }) {
						DemoIcon(DemoIcon.MoreVert)
					}
				},
				modifier = Modifier.fillMaxWidth(),
			) {
				clickableItem(onClick = {}, label = "Copy", icon = { DemoIcon(DemoIcon.ContentCopy) })
				clickableItem(onClick = {}, label = "Paste")
				toggleableItem(checked = vStar, label = "Star", onCheckedChange = { vStar = it }, icon = { DemoIcon(DemoIcon.Star) })
				clickableItem(onClick = {}, label = "Delete", icon = { DemoIcon(DemoIcon.Delete) })
			}
		}

		Section("SplitButtonLayout", "Primary action + attached menu trigger") {
			var vChecked by remember { mutableStateOf(false) }
			SplitButtonLayout(
				leadingButton = {
					SplitButtonDefaults.LeadingButton(onClick = {}) { Text("Send") }
				},
				trailingButton = {
					SplitButtonDefaults.TrailingButton(checked = vChecked, onCheckedChange = { vChecked = it }) {
						DemoIcon(DemoIcon.KeyboardArrowDown)
					}
				},
			)
		}

		Section("ToggleButton variants", "Base / Elevated / Outlined / Tonal checked-state buttons") {
			var vBase by remember { mutableStateOf(false) }
			var vA by remember { mutableStateOf(true) }
			var vB by remember { mutableStateOf(false) }
			var vC by remember { mutableStateOf(true) }
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
				ToggleButton(checked = vBase, onCheckedChange = { vBase = it }) { Text("Base") }
				ElevatedToggleButton(checked = vA, onCheckedChange = { vA = it }) { Text("Elevated") }
				OutlinedToggleButton(checked = vB, onCheckedChange = { vB = it }) { Text("Outlined") }
				TonalToggleButton(checked = vC, onCheckedChange = { vC = it }) { Text("Tonal") }
			}
		}

		Section("Icon toggle buttons", "Plain / Filled / FilledTonal / Outlined + FilledTonalIconButton") {
			var v1 by remember { mutableStateOf(true) }
			var v2 by remember { mutableStateOf(false) }
			var v3 by remember { mutableStateOf(true) }
			var v4 by remember { mutableStateOf(false) }
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				IconToggleButton(checked = v1, onCheckedChange = { v1 = it }) { DemoIcon(if (v1) DemoIcon.Star else DemoIcon.Bookmark) }
				FilledIconToggleButton(checked = v2, onCheckedChange = { v2 = it }) { DemoIcon(DemoIcon.Favorite) }
				FilledTonalIconToggleButton(checked = v3, onCheckedChange = { v3 = it }) { DemoIcon(DemoIcon.Bookmark) }
				OutlinedIconToggleButton(checked = v4, onCheckedChange = { v4 = it }) { DemoIcon(DemoIcon.Notifications) }
				FilledTonalIconButton(onClick = {}) { DemoIcon(DemoIcon.Settings) }
			}
		}

		Section("MultiChoiceSegmentedButtonRow", "Independently-toggleable segments") {
			val vLabels = listOf("Bold", "Italic", "Underline")
			val vChecked = remember { mutableStateOf(setOf(0)) }
			MultiChoiceSegmentedButtonRow {
				vLabels.forEachIndexed { vIdx, vLabel ->
					SegmentedButton(
						checked = vIdx in vChecked.value,
						onCheckedChange = { vOn ->
							vChecked.value = if (vOn) vChecked.value + vIdx else vChecked.value - vIdx
						},
						shape = SegmentedButtonDefaults.itemShape(vIdx, vLabels.size),
					) {
						Text(vLabel)
					}
				}
			}
		}
	}
}
