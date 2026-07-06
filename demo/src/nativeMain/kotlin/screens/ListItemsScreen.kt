package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined

// Material3 — ListItem rows, HorizontalDivider / VerticalDivider, Badge / BadgedBox.
@Composable
internal fun ListItemsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Lists, Dividers & Badges",
			"material3 ListItem (headline / supporting / leading / trailing), HorizontalDivider, VerticalDivider, Badge.",
		)

		Section("ListItem", "Slot-based rows separated by HorizontalDividers") {
			Column {
				ListItem(
					headlineContent = { Text("Wi-Fi") },
					supportingContent = { Text("Connected · HomeNet") },
					leadingContent = { MaterialSymbolsOutlined(MaterialSymbols.Settings, size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
					trailingContent = { Text("On") },
				)
				HorizontalDivider()
				ListItem(
					headlineContent = { Text("Notifications") },
					supportingContent = { Text("3 new") },
					leadingContent = { MaterialSymbolsOutlined(MaterialSymbols.Notifications, size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
				)
				HorizontalDivider()
				ListItem(
					headlineContent = { Text("Storage") },
					supportingContent = { Text("64% used") },
					leadingContent = { MaterialSymbolsOutlined(MaterialSymbols.Save, size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
				)
			}
		}

		Section("Dividers", "HorizontalDivider (above) and VerticalDivider (between labels)") {
			Row(
				modifier = Modifier.height(40.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text("Left", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
				VerticalDivider()
				Text("Middle", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
				VerticalDivider()
				Text("Right", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
			}
		}

		Section("Badge / BadgedBox", "Count and dot overlays anchored to an icon") {
			Row(
				horizontalArrangement = Arrangement.spacedBy(28.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				BadgedBox(badge = { Badge { Text("8") } }) {
					MaterialSymbolsOutlined(MaterialSymbols.Notifications, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface)
				}
				BadgedBox(badge = { Badge() }) {
					MaterialSymbolsOutlined(MaterialSymbols.Favorite, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface)
				}
			}
		}
	}
}
