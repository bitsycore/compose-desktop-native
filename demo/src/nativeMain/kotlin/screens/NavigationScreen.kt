package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined

private val NavItems = listOf(
	"Home" to MaterialSymbols.Home,
	"Search" to MaterialSymbols.Search,
	"Profile" to MaterialSymbols.Person,
	"Settings" to MaterialSymbols.Settings,
)

// Material3 — TabRow + Tab, NavigationBar, NavigationRail.
@Composable
internal fun NavigationScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Navigation & Tabs", "material3 TabRow + Tab, NavigationBar, NavigationRail.")

		Section("TabRow", "Underlined tab strip with an animated indicator") {
			var vSel by remember { mutableStateOf(0) }
			val vTabs = listOf("Overview", "Activity", "Settings")
			Column {
				TabRow(selectedTabIndex = vSel) {
					vTabs.forEachIndexed { vIndex, vTitle ->
						Tab(selected = vSel == vIndex, onClick = { vSel = vIndex }, text = { Text(vTitle) })
					}
				}
				Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
					Text("Selected tab: ${vTabs[vSel]}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
				}
			}
		}

		Section("NavigationBar", "Bottom navigation — icon + label items") {
			var vSel by remember { mutableStateOf(0) }
			// windowInsets(0): the demo root doesn't seed LocalPlatformWindowInsets
			// (no system bars on desktop), so opt out of the default inset padding.
			NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
				NavItems.forEachIndexed { vIndex, vItem ->
					NavigationBarItem(
						selected = vSel == vIndex,
						onClick = { vSel = vIndex },
						icon = {
							MaterialSymbolsOutlined(
								vItem.second,
								size = 24.dp,
								tint = if (vSel == vIndex) MaterialTheme.colorScheme.onSecondaryContainer
								else MaterialTheme.colorScheme.onSurfaceVariant,
							)
						},
						label = { Text(vItem.first) },
					)
				}
			}
		}

		Section("NavigationRail", "Side navigation — vertical icon + label rail") {
			var vSel by remember { mutableStateOf(0) }
			NavigationRail(modifier = Modifier.height(260.dp), windowInsets = WindowInsets(0, 0, 0, 0)) {
				NavItems.forEachIndexed { vIndex, vItem ->
					NavigationRailItem(
						selected = vSel == vIndex,
						onClick = { vSel = vIndex },
						icon = {
							MaterialSymbolsOutlined(
								vItem.second,
								size = 24.dp,
								tint = if (vSel == vIndex) MaterialTheme.colorScheme.onSecondaryContainer
								else MaterialTheme.colorScheme.onSurfaceVariant,
							)
						},
						label = { Text(vItem.first) },
					)
				}
			}
		}
	}
}
