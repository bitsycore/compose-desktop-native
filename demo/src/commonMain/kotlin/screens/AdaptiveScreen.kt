package screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.coroutines.launch

/**
 Probe seam: whether [AdaptiveScreen] last laid out two panes or one. The native
 `--adaptivetest` probe resizes the window and asserts this flips, which is the
 only way to prove the fold reacts to a LIVE resize rather than just to the size
 the window happened to open at. Not part of the demo UI.
 */
internal var gAdaptiveTwoPane: Boolean = false
	private set

/**
 material3-adaptive running on desktop native - resize the window and watch it fold.

 Upstream `org.jetbrains.compose.material3.adaptive:*` publishes ios + macosArm64
 only, so this screen doubles as the runtime proof that the vendored
 `:compose:material3:adaptive:*` modules work on linux and mingw too. It exercises
 three of the four modules: `adaptive` ([currentWindowAdaptiveInfo]),
 `adaptive-layout` ([ListDetailPaneScaffold]) and `adaptive-navigation` (the
 navigator that drives the fold).

 **Why the directive is computed here instead of being left to default.**
 [rememberListDetailPaneScaffoldNavigator] defaults its directive to the WINDOW
 size class, but this screen does not get the whole window - the demo shell spends
 190dp on the sidebar plus 48dp of padding. Defaulting would fold ~240dp too late:
 the panes would still be side by side in a region already too cramped for them.
 So the size class is computed from the width this screen actually gets
 ([BoxWithConstraints]), which is what a real pane-hosting app should do too. The
 header prints both numbers so the difference is visible while resizing.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveScreen() {
	val vItems = remember { List(24) { "Item ${it + 1}" } }
	var vSelected by remember { mutableStateOf(vItems.first()) }
	val vScope = rememberCoroutineScope()

	val vWindowDpSize = currentWindowDpSize()
	val vWindowClass = currentWindowAdaptiveInfo().windowSizeClass

	Column(
		Modifier.fillMaxWidth().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text("material3-adaptive", style = MaterialTheme.typography.titleLarge)

		BoxWithConstraints(Modifier.fillMaxWidth()) {
			// The space THIS screen was handed, not the window. maxHeight is
			// infinite here (the shell hosts every screen in a verticalScroll), so
			// the pane height is derived from the window instead - see below.
			val vPaneWidth = maxWidth
			val vPaneHeight = (vWindowDpSize.height - 200.dp).coerceIn(260.dp, 760.dp)
			val vPaneClass = sizeClassFor(vPaneWidth, vPaneHeight)

			val vNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>(
				scaffoldDirective = calculatePaneScaffoldDirective(
					WindowAdaptiveInfo(vPaneClass, Posture()),
				),
			)
			val vTwoPane = vNavigator.scaffoldDirective.maxHorizontalPartitions > 1
			SideEffect { gAdaptiveTwoPane = vTwoPane }

			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				ReadoutCard(
					vWindowLine = "window ${vWindowDpSize.width.value.toInt()}x" +
						"${vWindowDpSize.height.value.toInt()}dp -> " +
						"minWidth ${vWindowClass.minWidthDp}dp",
					vPaneLine = "this screen ${vPaneWidth.value.toInt()}x" +
						"${vPaneHeight.value.toInt()}dp -> " +
						"minWidth ${vPaneClass.minWidthDp}dp",
					vVerdict = if (vTwoPane) {
						"Wide enough: list and detail sit side by side. " +
							"Narrow the window to fold them."
					} else {
						"Folded: one pane at a time. Pick an item to open the " +
							"detail, then Back. Widen the window to unfold."
					},
				)

				ListDetailPaneScaffold(
					modifier = Modifier.fillMaxWidth().height(vPaneHeight),
					directive = vNavigator.scaffoldDirective,
					value = vNavigator.scaffoldValue,
					listPane = {
						AnimatedPane {
							Card(Modifier.fillMaxSize()) {
								LazyColumn {
									items(vItems) { vItem ->
										val vIsSelected = vItem == vSelected
										ListItem(
											headlineContent = { Text(vItem) },
											colors = if (vIsSelected) {
												ListItemDefaults.colors(
													containerColor =
														MaterialTheme.colorScheme.secondaryContainer,
												)
											} else {
												ListItemDefaults.colors()
											},
											modifier = Modifier.fillMaxWidth().clickable {
												vSelected = vItem
												vScope.launch {
													vNavigator.navigateTo(
														ListDetailPaneScaffoldRole.Detail,
													)
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
									verticalArrangement = Arrangement.spacedBy(12.dp),
								) {
									Text(
										vSelected,
										style = MaterialTheme.typography.headlineSmall,
									)
									Text(
										if (vTwoPane) {
											"Detail pane, side by side with the list " +
												"because there is room for both."
										} else {
											"Detail pane, filling the scaffold because " +
												"there is only room for one."
										},
										style = MaterialTheme.typography.bodyMedium,
									)
									// Only reachable when folded - with both panes up
									// there is nothing to go back to.
									if (vNavigator.canNavigateBack()) {
										OutlinedButton(
											onClick = {
												vScope.launch { vNavigator.navigateBack() }
											},
										) { Text("Back to list") }
									}
								}
							}
						}
					},
				)
			}
		}
	}
}

/** The live size readout above the scaffold - the whole point of the screen is
   watching these three lines change as the window is dragged. */
@Composable
private fun ReadoutCard(vWindowLine: String, vPaneLine: String, vVerdict: String) {
	Card(
		Modifier.fillMaxWidth(),
		colors = CardDefaults.outlinedCardColors(),
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column(
			Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(vWindowLine, style = MaterialTheme.typography.bodySmall)
			Text(vPaneLine, style = MaterialTheme.typography.bodySmall)
			Text(
				vVerdict,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.primary,
			)
		}
	}
}

/**
 The [WindowSizeClass] for an arbitrary region.

 window-core only offers this for the window itself; the breakpoint constants are
 public, so applying them to a sub-region is just a table lookup.
 */
private fun sizeClassFor(inWidth: Dp, inHeight: Dp): WindowSizeClass {
	val vMinWidth = when {
		inWidth.value >= WIDTH_DP_EXPANDED_LOWER_BOUND -> WIDTH_DP_EXPANDED_LOWER_BOUND
		inWidth.value >= WIDTH_DP_MEDIUM_LOWER_BOUND -> WIDTH_DP_MEDIUM_LOWER_BOUND
		else -> 0
	}
	val vMinHeight = when {
		inHeight.value >= HEIGHT_DP_EXPANDED_LOWER_BOUND -> HEIGHT_DP_EXPANDED_LOWER_BOUND
		inHeight.value >= HEIGHT_DP_MEDIUM_LOWER_BOUND -> HEIGHT_DP_MEDIUM_LOWER_BOUND
		else -> 0
	}
	return WindowSizeClass(vMinWidth, vMinHeight)
}
