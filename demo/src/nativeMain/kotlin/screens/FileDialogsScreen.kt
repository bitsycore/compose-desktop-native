package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.showOpenFileDialog
import com.compose.sdl.showSaveFileDialog

// ==================
// MARK: File dialogs (SDL3 native)
// ==================

/** The OS-native Open / Save As dialogs, via SDL3. The result handler runs on
   the compose main-loop thread, so writing snapshot state from it (as below)
   is picked up on the next frame — no manual thread hop needed. No upstream
   Compose Multiplatform analog; this is a Native · Desktop feature. */
@Composable
internal fun FileDialogsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("File dialogs", "SDL3 native Open / Save As dialogs — showOpenFileDialog / showSaveFileDialog.")

		var result by remember { mutableStateOf("(no selection yet)") }

		Section("showOpenFileDialog", "Single-selection OS Open dialog; the callback runs on the main loop") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Button(onClick = {
					showOpenFileDialog { path -> result = path ?: "(cancelled)" }
				}) { Text("Open file…") }
			}
		}

		Section("showSaveFileDialog", "OS Save As dialog seeded with a suggested file name") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				OutlinedButton(onClick = {
					showSaveFileDialog("untitled.txt") { path -> result = path ?: "(cancelled)" }
				}) { Text("Save file…") }
			}
		}

		Section("Result", "The path returned by the last dialog") {
			Text(result, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
		}
	}
}
