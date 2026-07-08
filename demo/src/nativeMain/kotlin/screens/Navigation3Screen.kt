package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

// Navigation 3 on the SDL/native stack: androidx.navigation3-runtime (a real Maven
// artifact for mingwX64 + macOS/Linux) drives the back stack; the project's own
// NavDisplay (:navigation3-ui) renders the top entry with an animated transition.
// navigation3-ui (NavDisplay) has no K/N desktop artifact upstream, so it's reimplemented.

private sealed interface Nav3Route : NavKey
private data object Nav3Home : Nav3Route
private data class Nav3Detail(val id: Int) : Nav3Route

@Composable
internal fun Navigation3Screen() {
	// A NavBackStack is a snapshot-backed MutableList<NavKey> — push/pop directly.
	val backStack = remember { NavBackStack<Nav3Route>(Nav3Home) }

	Column(
		modifier = Modifier.fillMaxWidth().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		ScreenTitle("Navigation 3", "androidx.navigation3 runtime + a project NavDisplay (SDL/native)")

		Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
			OutlinedButton(onClick = { backStack.removeLastOrNull() }, enabled = backStack.size > 1) {
				Text("Back")
			}
			Text("back stack depth: ${backStack.size}")
		}

		NavDisplay(
			backStack = backStack,
			modifier = Modifier.fillMaxWidth(),
			entryProvider = entryProvider {
				entry<Nav3Home> {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Home — pick a detail to navigate to")
						repeat(3) { i ->
							Button(onClick = { backStack.add(Nav3Detail(i)) }) { Text("Open detail #$i") }
						}
					}
				}
				entry<Nav3Detail> { key ->
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Detail #${key.id}")
						Button(onClick = { backStack.add(Nav3Detail(key.id + 1)) }) {
							Text("Go deeper → #${key.id + 1}")
						}
					}
				}
			},
		)
	}
}
