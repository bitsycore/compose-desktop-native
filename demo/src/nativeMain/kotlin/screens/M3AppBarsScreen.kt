@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AppBarColumn
import androidx.compose.material3.AppBarOverflowIndicator
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.shim.DemoIcon

// Material3 — the full top/bottom app-bar family + the AppBarRow/Column DSL.
@Composable
internal fun M3AppBarsScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("App bars", "TopAppBar variants, bottom bars, and the AppBarRow/Column overflow DSL.")

		Section("TopAppBar / CenterAlignedTopAppBar", "Single-row title bars") {
			TopAppBar(
				title = { Text("TopAppBar") },
				navigationIcon = { BarIcon(DemoIcon.Menu) },
				actions = { BarIcon(DemoIcon.Search); BarIcon(DemoIcon.MoreVert) },
			)
			CenterAlignedTopAppBar(
				title = { Text("CenterAligned") },
				navigationIcon = { BarIcon(DemoIcon.ArrowBack) },
				actions = { BarIcon(DemoIcon.MoreVert) },
			)
		}

		Section("MediumTopAppBar / LargeTopAppBar", "Two-row title bars (collapsed state shown — no scroll linkage here)") {
			MediumTopAppBar(title = { Text("MediumTopAppBar") }, navigationIcon = { BarIcon(DemoIcon.Menu) })
			LargeTopAppBar(title = { Text("LargeTopAppBar") }, navigationIcon = { BarIcon(DemoIcon.Menu) })
		}

		Section("Flexible top app bars", "Expressive variants with subtitle slots") {
			MediumFlexibleTopAppBar(
				title = { Text("MediumFlexible") },
				subtitle = { Text("with a subtitle") },
			)
			LargeFlexibleTopAppBar(
				title = { Text("LargeFlexible") },
				subtitle = { Text("with a subtitle") },
			)
		}

		Section("TwoRowsTopAppBar", "Title lambda receives the expanded flag") {
			TwoRowsTopAppBar(title = { vExpanded -> Text(if (vExpanded) "Expanded title" else "Collapsed") })
		}

		Section("BottomAppBar / FlexibleBottomAppBar", "Bottom action strips") {
			BottomAppBar(
				actions = {
					BarIcon(DemoIcon.Check)
					BarIcon(DemoIcon.Edit)
					BarIcon(DemoIcon.Delete)
				},
			)
			FlexibleBottomAppBar {
				BarIcon(DemoIcon.Home)
				BarIcon(DemoIcon.Search)
				BarIcon(DemoIcon.Settings)
			}
		}

		Section("AppBarRow / AppBarColumn", "DSL items overflow into a menu when space runs out") {
			var vChecked by remember { mutableStateOf(false) }
			AppBarRow(
				modifier = Modifier.fillMaxWidth(),
				// The stock overflow affordance, passed explicitly (it's also
				// the default) so the component is exercised by name.
				overflowIndicator = { vMenuState -> AppBarOverflowIndicator(vMenuState) },
			) {
				clickableItem(onClick = {}, icon = { BarGlyph(DemoIcon.Home) }, label = "Home")
				clickableItem(onClick = {}, icon = { BarGlyph(DemoIcon.Search) }, label = "Search")
				toggleableItem(
					checked = vChecked,
					onCheckedChange = { vChecked = it },
					icon = { BarGlyph(DemoIcon.Star) },
					label = "Favourite",
				)
				clickableItem(onClick = {}, icon = { BarGlyph(DemoIcon.Settings) }, label = "Settings")
			}
			Box(modifier = Modifier.height(160.dp)) {
				AppBarColumn {
					clickableItem(onClick = {}, icon = { BarGlyph(DemoIcon.Home) }, label = "Home")
					clickableItem(onClick = {}, icon = { BarGlyph(DemoIcon.Edit) }, label = "Edit")
					clickableItem(onClick = {}, icon = { BarGlyph(DemoIcon.Delete) }, label = "Delete")
				}
			}
		}
	}
}

// IconButton-wrapped symbol for app-bar slots.
@Composable
private fun BarIcon(icon: DemoIcon) {
	IconButton(onClick = {}) { BarGlyph(icon) }
}

// Bare symbol glyph for DSL icon slots (the DSL provides its own button).
@Composable
private fun BarGlyph(icon: DemoIcon) {
	DemoIcon(icon)
}
