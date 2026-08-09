@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon
import kotlinx.coroutines.launch

private val RailItems = listOf(
	"Home" to DemoIcon.Home,
	"Search" to DemoIcon.Search,
	"Settings" to DemoIcon.Settings,
)

// Material3 - WideNavigationRail / ModalWideNavigationRail / ShortNavigationBar.
@Composable
internal fun M3RailsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Nav rails+", "Wide rail (collapsible), modal wide rail, and the short navigation bar.")

		Section("WideNavigationRail", "Toggles between collapsed (icons) and expanded (icons + labels)") {
			val vState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)
			val vScope = rememberCoroutineScope()
			var vSelected by remember { mutableStateOf(0) }
			Row {
				Box(modifier = Modifier.height(280.dp)) {
					WideNavigationRail(state = vState) {
						RailItems.forEachIndexed { vIdx, (vLabel, vGlyph) ->
							WideNavigationRailItem(
								selected = vSelected == vIdx,
								onClick = { vSelected = vIdx },
								icon = { DemoIcon(vGlyph) },
								label = { Text(vLabel) },
								railExpanded = vState.targetValue == WideNavigationRailValue.Expanded,
							)
						}
					}
				}
				Box(modifier = Modifier.height(280.dp), contentAlignment = Alignment.Center) {
					Button(onClick = { vScope.launch { vState.toggle() } }) { Text("Toggle rail") }
				}
			}
		}

		Section("ModalWideNavigationRail", "Expands as a modal overlay above the content") {
			val vState = rememberWideNavigationRailState(WideNavigationRailValue.Collapsed)
			val vScope = rememberCoroutineScope()
			var vSelected by remember { mutableStateOf(0) }
			Row {
				Box(modifier = Modifier.height(260.dp)) {
					ModalWideNavigationRail(state = vState) {
						RailItems.forEachIndexed { vIdx, (vLabel, vGlyph) ->
							WideNavigationRailItem(
								selected = vSelected == vIdx,
								onClick = { vSelected = vIdx },
								icon = { DemoIcon(vGlyph) },
								label = { Text(vLabel) },
								railExpanded = vState.targetValue == WideNavigationRailValue.Expanded,
							)
						}
					}
				}
				Box(modifier = Modifier.height(260.dp), contentAlignment = Alignment.Center) {
					Button(onClick = { vScope.launch { vState.toggle() } }) { Text("Expand modal rail") }
				}
			}
		}

		Section("ShortNavigationBar", "The compact bottom navigation bar") {
			var vSelected by remember { mutableStateOf(0) }
			ShortNavigationBar(modifier = Modifier.fillMaxWidth()) {
				RailItems.forEachIndexed { vIdx, (vLabel, vGlyph) ->
					ShortNavigationBarItem(
						selected = vSelected == vIdx,
						onClick = { vSelected = vIdx },
						icon = { DemoIcon(vGlyph) },
						label = { Text(vLabel) },
					)
				}
			}
		}
	}
}
