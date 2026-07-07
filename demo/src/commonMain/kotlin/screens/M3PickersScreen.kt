@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Material3 — date and time pickers.
@Composable
internal fun M3PickersScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Pickers", "DatePicker, DateRangePicker, TimePicker, TimeInput, TimePickerDialog.")

		Section("DatePicker", "Full inline calendar") {
			DatePicker(state = rememberDatePickerState())
		}

		Section("DateRangePicker", "Start + end date selection") {
			// The range picker scrolls its months in an internal LazyColumn —
			// it must get a BOUNDED height (the screen's outer scroll column
			// would otherwise measure it with infinite max height and crash).
			DateRangePicker(
				state = rememberDateRangePickerState(),
				modifier = Modifier.fillMaxWidth().height(460.dp),
			)
		}

		Section("TimePicker", "Clock-dial time selection") {
			TimePicker(state = rememberTimePickerState(initialHour = 14, initialMinute = 30))
		}

		Section("TimeInput", "Keyboard-first variant of the time picker") {
			TimeInput(state = rememberTimePickerState(initialHour = 9, initialMinute = 15))
		}

		Section("TimePickerDialog", "The picker hosted in its standard dialog") {
			var vOpen by remember { mutableStateOf(false) }
			val vState = rememberTimePickerState(initialHour = 8, initialMinute = 0)
			Button(onClick = { vOpen = true }) { Text("Pick a time") }
			if (vOpen) {
				TimePickerDialog(
					onDismissRequest = { vOpen = false },
					confirmButton = { TextButton(onClick = { vOpen = false }) { Text("OK") } },
					title = { Text("Select time") },
				) {
					TimePicker(state = vState)
				}
			}
		}
	}
}
