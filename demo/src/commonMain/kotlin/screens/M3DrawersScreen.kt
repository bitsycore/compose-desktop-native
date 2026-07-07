@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scrim
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Material3 — the navigation-drawer family (modal / dismissible / permanent) + Scrim.
@Composable
internal fun M3DrawersScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Drawers", "Modal / Dismissible / Permanent navigation drawers, drawer items, Scrim.")

		Section("ModalNavigationDrawer", "Slides over the content with a scrim — open with the button") {
			val vState = rememberDrawerState(DrawerValue.Closed)
			val vScope = rememberCoroutineScope()
			Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
				ModalNavigationDrawer(
					drawerState = vState,
					drawerContent = {
						ModalDrawerSheet(modifier = Modifier.fillMaxSize()) {
							DrawerItems()
						}
					},
				) {
					Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Button(onClick = { vScope.launch { vState.open() } }) { Text("Open drawer") }
					}
				}
			}
		}

		Section("DismissibleNavigationDrawer", "Pushes the content aside instead of overlaying it") {
			val vState = rememberDrawerState(DrawerValue.Open)
			val vScope = rememberCoroutineScope()
			Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
				DismissibleNavigationDrawer(
					drawerState = vState,
					drawerContent = {
						DismissibleDrawerSheet(modifier = Modifier.fillMaxSize()) {
							DrawerItems()
						}
					},
				) {
					Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Button(onClick = { vScope.launch { if (vState.isOpen) vState.close() else vState.open() } }) {
							Text("Toggle")
						}
					}
				}
			}
		}

		Section("PermanentNavigationDrawer", "Always visible — desktop-style side navigation") {
			Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
				PermanentNavigationDrawer(
					drawerContent = {
						PermanentDrawerSheet(modifier = Modifier.fillMaxSize()) {
							DrawerItems()
						}
					},
				) {
					Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Text("Content pane")
					}
				}
			}
		}

		Section("Scrim", "The standalone dimming layer modal components use — click to hide") {
			var vVisible by remember { mutableStateOf(true) }
			Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
				Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
					Button(onClick = { vVisible = true }) { Text("Show scrim") }
				}
				if (vVisible) {
					Scrim(contentDescription = "demo scrim", modifier = Modifier.fillMaxSize(), onClick = { vVisible = false })
				}
			}
		}
	}
}

// Three canned items shared by every drawer sheet sample.
@Composable
private fun DrawerItems() {
	var vSelected by remember { mutableStateOf(0) }
	Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
		listOf("Inbox", "Sent", "Archive").forEachIndexed { vIdx, vLabel ->
			NavigationDrawerItem(
				label = { Text(vLabel) },
				selected = vSelected == vIdx,
				onClick = { vSelected = vIdx },
			)
		}
	}
}
