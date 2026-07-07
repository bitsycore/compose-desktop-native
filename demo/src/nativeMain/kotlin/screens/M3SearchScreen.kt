@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Button
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedDockedSearchBarWithGap
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon
import kotlinx.coroutines.launch

// Material3 — the search-bar family (state-based + docked + app-bar-integrated).
@Composable
internal fun M3SearchScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Search", "SearchBar, TopSearchBar, DockedSearchBar, AppBarWithSearch.")

		Section("SearchBar", "Click the field to expand into the full-screen suggestion surface") {
			val vState = rememberSearchBarState()
			val vText = rememberTextFieldState()
			val vScope = rememberCoroutineScope()
			val vInput: @Composable () -> Unit = {
				SearchBarDefaults.InputField(
					textFieldState = vText,
					searchBarState = vState,
					onSearch = { vScope.launch { vState.animateToCollapsed() } },
					placeholder = { Text("Search the docs…") },
					leadingIcon = { DemoIcon(DemoIcon.Search) },
				)
			}
			SearchBar(state = vState, inputField = vInput)
			ExpandedFullScreenSearchBar(state = vState, inputField = vInput) {
				Column {
					for (vHit in listOf("Compose", "SDL3", "Kotlin/Native")) {
						ListItem(
							headlineContent = { Text(vHit) },
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			}
		}

		Section("TopSearchBar", "Scroll-aware variant meant to pin at the top of a screen") {
			val vState = rememberSearchBarState()
			val vText = rememberTextFieldState()
			TopSearchBar(
				state = vState,
				inputField = {
					SearchBarDefaults.InputField(
						textFieldState = vText,
						searchBarState = vState,
						onSearch = {},
						placeholder = { Text("TopSearchBar") },
					)
				},
			)
		}

		Section("DockedSearchBar", "Expands in place as a dropdown instead of full-screen") {
			var vQuery by remember { mutableStateOf("") }
			var vExpanded by remember { mutableStateOf(false) }
			DockedSearchBar(
				inputField = {
					SearchBarDefaults.InputField(
						query = vQuery,
						onQueryChange = { vQuery = it },
						onSearch = { vExpanded = false },
						expanded = vExpanded,
						onExpandedChange = { vExpanded = it },
						placeholder = { Text("DockedSearchBar") },
					)
				},
				expanded = vExpanded,
				onExpandedChange = { vExpanded = it },
			) {
				for (vHit in listOf("Alpha", "Beta", "Gamma")) {
					ListItem(headlineContent = { Text(vHit) }, modifier = Modifier.fillMaxWidth())
				}
			}
		}

		Section("Expanded variants", "Direct calls to the expanded morphologies — expand with the button") {
			val vState = rememberSearchBarState()
			val vText = rememberTextFieldState()
			val vScope = rememberCoroutineScope()
			val vInput: @Composable () -> Unit = {
				SearchBarDefaults.InputField(
					textFieldState = vText,
					searchBarState = vState,
					onSearch = { vScope.launch { vState.animateToCollapsed() } },
					placeholder = { Text("Docked, expanded in place") },
				)
			}
			Button(onClick = { vScope.launch { vState.animateToExpanded() } }) { Text("Expand docked variants") }
			SearchBar(state = vState, inputField = vInput)
			// Each renders only while the state is Expanded; all three share it.
			ExpandedDockedSearchBar(state = vState, inputField = vInput) {
				ListItem(headlineContent = { Text("ExpandedDockedSearchBar") })
			}
			ExpandedDockedSearchBarWithGap(state = vState, inputField = vInput) {
				ListItem(headlineContent = { Text("ExpandedDockedSearchBarWithGap") })
			}
			ExpandedFullScreenContainedSearchBar(state = vState, inputField = vInput) {
				ListItem(headlineContent = { Text("ExpandedFullScreenContainedSearchBar") })
			}
		}

		Section("AppBarWithSearch", "TopAppBar and search field fused into one bar") {
			val vState = rememberSearchBarState()
			val vText = rememberTextFieldState()
			AppBarWithSearch(
				state = vState,
				inputField = {
					SearchBarDefaults.InputField(
						textFieldState = vText,
						searchBarState = vState,
						onSearch = {},
						placeholder = { Text("Search in app bar") },
					)
				},
				navigationIcon = {
					IconButton(onClick = {}) { DemoIcon(DemoIcon.Menu) }
				},
			)
		}
	}
}
