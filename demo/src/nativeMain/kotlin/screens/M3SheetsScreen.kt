@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Material3 — bottom sheets, swipe-to-dismiss, drag handles.
@Composable
internal fun M3SheetsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Sheets", "ModalBottomSheet, BottomSheetScaffold, SwipeToDismissBox, VerticalDragHandle.")

		Section("ModalBottomSheet", "Opens over the whole window with a scrim") {
			var vOpen by remember { mutableStateOf(false) }
			Button(onClick = { vOpen = true }) { Text("Show modal sheet") }
			if (vOpen) {
				ModalBottomSheet(
					onDismissRequest = { vOpen = false },
					sheetState = rememberModalBottomSheetState(),
				) {
					Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Sheet content", color = MaterialTheme.colorScheme.onSurface)
						Button(onClick = { vOpen = false }) { Text("Close") }
					}
				}
			}
		}

		Section("BottomSheetScaffold", "Persistent sheet docked to the bottom of its container") {
			Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
				BottomSheetScaffold(
					scaffoldState = rememberBottomSheetScaffoldState(),
					sheetContent = {
						Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
							Text("Partially expanded sheet — drag the handle")
							Text("Second line of sheet content")
						}
					},
				) { vPadding ->
					Box(modifier = Modifier.fillMaxSize().padding(vPadding), contentAlignment = Alignment.Center) {
						Text("Scaffold body")
					}
				}
			}
		}

		Section("SwipeToDismissBox", "Drag the row horizontally to reveal the background") {
			val vState = rememberSwipeToDismissBoxState()
			SwipeToDismissBox(
				state = vState,
				backgroundContent = {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								when (vState.dismissDirection) {
									SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
									else -> MaterialTheme.colorScheme.tertiaryContainer
								},
							),
					)
				},
			) {
				ListItem(headlineContent = { Text("Swipe me left or right") })
			}
		}

		Section("BottomSheet", "The standalone sheet primitive ModalBottomSheet builds on") {
			Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
				BottomSheet(
					state = rememberBottomSheetState(initialValue = SheetValue.Expanded),
				) {
					Column(modifier = Modifier.padding(20.dp)) {
						Text("Standalone BottomSheet, expanded in place")
					}
				}
			}
		}

		Section("VerticalDragHandle", "Grip affordance for resizable panes") {
			Row(modifier = Modifier.height(80.dp), verticalAlignment = Alignment.CenterVertically) {
				Box(modifier = Modifier.fillMaxSize().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant))
				VerticalDragHandle()
				Box(modifier = Modifier.fillMaxSize().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant))
			}
		}
	}
}
