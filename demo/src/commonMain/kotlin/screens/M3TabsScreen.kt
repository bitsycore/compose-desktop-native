@file:OptIn(ExperimentalMaterial3Api::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon

private val TabLabels = listOf("Overview", "Details", "Settings")
private val ManyTabs = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight")

// Material3 — the TabRow family + LeadingIconTab.
@Composable
internal fun M3TabsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Tabs", "Primary / Secondary tab rows, scrollable variants, LeadingIconTab.")

		Section("PrimaryTabRow", "Rounded indicator hugging the selected tab") {
			var vSel by remember { mutableStateOf(0) }
			PrimaryTabRow(selectedTabIndex = vSel) {
				TabLabels.forEachIndexed { vIdx, vLabel ->
					Tab(selected = vSel == vIdx, onClick = { vSel = vIdx }, text = { Text(vLabel) })
				}
			}
		}

		Section("SecondaryTabRow", "Full-width underline indicator") {
			var vSel by remember { mutableStateOf(1) }
			SecondaryTabRow(selectedTabIndex = vSel) {
				TabLabels.forEachIndexed { vIdx, vLabel ->
					Tab(selected = vSel == vIdx, onClick = { vSel = vIdx }, text = { Text(vLabel) })
				}
			}
		}

		Section("PrimaryScrollableTabRow", "Scrolls horizontally when tabs overflow") {
			var vSel by remember { mutableStateOf(0) }
			PrimaryScrollableTabRow(selectedTabIndex = vSel) {
				ManyTabs.forEachIndexed { vIdx, vLabel ->
					Tab(selected = vSel == vIdx, onClick = { vSel = vIdx }, text = { Text(vLabel) })
				}
			}
		}

		Section("SecondaryScrollableTabRow", "Scrollable with the secondary indicator") {
			var vSel by remember { mutableStateOf(2) }
			SecondaryScrollableTabRow(selectedTabIndex = vSel) {
				ManyTabs.forEachIndexed { vIdx, vLabel ->
					Tab(selected = vSel == vIdx, onClick = { vSel = vIdx }, text = { Text(vLabel) })
				}
			}
		}

		Section("LeadingIconTab", "Icon and label on one line") {
			var vSel by remember { mutableStateOf(0) }
			PrimaryTabRow(selectedTabIndex = vSel) {
				listOf(
					"Home" to DemoIcon.Home,
					"Search" to DemoIcon.Search,
				).forEachIndexed { vIdx, (vLabel, vGlyph) ->
					LeadingIconTab(
						selected = vSel == vIdx,
						onClick = { vSel = vIdx },
						text = { Text(vLabel) },
						icon = { DemoIcon(vGlyph, size = 18.dp) },
					)
				}
			}
		}
	}
}
