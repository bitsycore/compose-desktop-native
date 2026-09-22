package screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 material3-adaptive running on desktop native.

 Upstream `org.jetbrains.compose.material3.adaptive:*` publishes ios + macosArm64
 only, so this screen is the runtime proof that the vendored
 `:compose:material3:adaptive:*` modules work on linux and mingw too. It exercises
 the three modules a consumer actually reaches for: `adaptive`
 ([currentWindowAdaptiveInfo] - the window size class, which updates live as the
 window is resized), `adaptive-layout` ([ListDetailPaneScaffold], which folds to a
 single pane at narrow widths) and `adaptive-navigation` (the navigator that drives
 that fold).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveScreen() {
	val vItems = remember { List(12) { "Item ${it + 1}" } }
	var vSelected by remember { mutableStateOf(vItems.first()) }

	val vNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
	val vScope = rememberCoroutineScope()
	val vAdaptiveInfo = currentWindowAdaptiveInfo()

	// The demo shell hosts every screen inside a verticalScroll, so the scaffold
	// would be measured with an infinite height - it needs a bounded one.
	Column(
		Modifier.fillMaxWidth().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text("material3-adaptive", style = MaterialTheme.typography.titleLarge)
		Text(
			"Window ${currentWindowDpSize()}, size class ${vAdaptiveInfo.windowSizeClass}. " +
				"Resize the window - the scaffold folds to a single pane once it gets narrow.",
			style = MaterialTheme.typography.bodySmall,
		)

		ListDetailPaneScaffold(
			modifier = Modifier.fillMaxWidth().height(420.dp),
			directive = vNavigator.scaffoldDirective,
			value = vNavigator.scaffoldValue,
			listPane = {
				AnimatedPane {
					Card(Modifier.fillMaxSize()) {
						LazyColumn {
							items(vItems) { vItem ->
								ListItem(
									headlineContent = { Text(vItem) },
									modifier = Modifier.fillMaxWidth().clickable {
										vSelected = vItem
										vScope.launch {
											vNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
										}
									},
								)
							}
						}
					}
				}
			},
			detailPane = {
				AnimatedPane {
					Card(Modifier.fillMaxSize()) {
						Column(
							Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Text(vSelected, style = MaterialTheme.typography.headlineSmall)
							Text(
								"Detail pane. On a wide window this sits beside the list; " +
									"narrow the window and the scaffold shows one pane at a time.",
								style = MaterialTheme.typography.bodyMedium,
							)
						}
					}
				}
			},
		)
	}
}
