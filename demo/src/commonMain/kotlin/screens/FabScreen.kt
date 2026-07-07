package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon

// Material3 — FloatingActionButton family, IconButton variants, elevated/tonal buttons.
@Composable
internal fun FabScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"FAB, Icon & Tonal Buttons",
			"material3 FloatingActionButton family, IconButton variants, ElevatedButton / FilledTonalButton.",
		)

		Section("FloatingActionButton", "Regular / Small / Extended") {
			Row(
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				FloatingActionButton(onClick = {}) {
					DemoIcon(DemoIcon.Add, size = 24.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
				}
				SmallFloatingActionButton(onClick = {}) {
					DemoIcon(DemoIcon.Edit, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
				}
				ExtendedFloatingActionButton(onClick = {}) {
					DemoIcon(DemoIcon.Add, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
					Text("  Compose", color = MaterialTheme.colorScheme.onPrimaryContainer)
				}
			}
		}

		Section("IconButton", "Standard / Filled / Outlined") {
			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = {}) {
					DemoIcon(DemoIcon.Favorite, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
				}
				FilledIconButton(onClick = {}) {
					DemoIcon(DemoIcon.Star, size = 22.dp, tint = MaterialTheme.colorScheme.onPrimary)
				}
				OutlinedIconButton(onClick = {}) {
					DemoIcon(DemoIcon.Settings, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
				}
			}
		}

		Section("Elevated / Tonal buttons", "ElevatedButton + FilledTonalButton") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				ElevatedButton(onClick = {}) {
					Text("Elevated", color = MaterialTheme.colorScheme.primary)
				}
				FilledTonalButton(onClick = {}) {
					Text("Tonal", color = MaterialTheme.colorScheme.onSecondaryContainer)
				}
			}
		}
	}
}
