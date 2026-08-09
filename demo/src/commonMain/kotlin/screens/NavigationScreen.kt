package screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import demo.shim.DemoIcon

private val NavItems = listOf(
	"Home" to DemoIcon.Home,
	"Search" to DemoIcon.Search,
	"Profile" to DemoIcon.Person,
	"Settings" to DemoIcon.Settings,
)

// Material3 - TabRow + Tab, NavigationBar, NavigationRail.
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

		Section("NavigationBar", "Bottom navigation - icon + label items") {
			var vSel by remember { mutableStateOf(0) }
			// windowInsets(0): the demo root doesn't seed LocalPlatformWindowInsets
			// (no system bars on desktop), so opt out of the default inset padding.
			NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
				NavItems.forEachIndexed { vIndex, vItem ->
					NavigationBarItem(
						selected = vSel == vIndex,
						onClick = { vSel = vIndex },
						icon = {
							DemoIcon(
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

		Section("NavigationRail", "Side navigation - vertical icon + label rail") {
			var vSel by remember { mutableStateOf(0) }
			NavigationRail(modifier = Modifier.height(260.dp), windowInsets = WindowInsets(0, 0, 0, 0)) {
				NavItems.forEachIndexed { vIndex, vItem ->
					NavigationRailItem(
						selected = vSel == vIndex,
						onClick = { vSel = vIndex },
						icon = {
							DemoIcon(
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
