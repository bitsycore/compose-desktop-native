package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: LazyExtraScreen
// ==================

/* Demonstrates LazyRow (horizontal counterpart of LazyColumn) and
   LazyVerticalGrid with GridCells.Fixed + GridCells.Adaptive. */
@Composable
internal fun LazyExtraScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary
	val vOnSurface = MaterialTheme.colorScheme.onSurface

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"LazyRow + LazyVerticalGrid",
			"LazyRow scrolls horizontally, LazyVerticalGrid arranges items in a column-grid. Both " +
				"reuse the LazyList scope (item, items, itemsIndexed). Not yet virtualised — every " +
				"item composes each frame; the surrounding scroll modifier clips.",
		)

		Section("LazyRow", "30 items, horizontally scrollable. Drag or wheel to pan.") {
			LazyRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(30) { vIdx ->
					Box(
						modifier = Modifier
							.size(80.dp, 60.dp)
							.background(if (vIdx % 2 == 0) vPrimary else vSecondary, RoundedCornerShape(6.dp))
					) {
						Box(modifier = Modifier.padding(8.dp)) {
							Text("$vIdx", color = androidx.compose.ui.graphics.Color(0xFF000000), fontSize = 16.sp)
						}
					}
				}
			}
		}

		Section("LazyVerticalGrid — GridCells.Fixed(4)", "16 cells in 4 columns.") {
			LazyVerticalGrid(
				columns = GridCells.Fixed(4),
				modifier = Modifier.fillMaxWidth().height(220.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(16) { vIdx ->
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(48.dp)
							.background(if (vIdx % 2 == 0) vPrimary else vSecondary, RoundedCornerShape(6.dp))
					) {
						Box(modifier = Modifier.padding(8.dp)) {
							Text("$vIdx", color = androidx.compose.ui.graphics.Color(0xFF000000), fontSize = 14.sp)
						}
					}
				}
			}
		}

		Section(
			"LazyVerticalGrid — GridCells.Adaptive(minSize = 96.dp)",
			"Column count adapts to the parent width — wider window → more columns.",
		) {
			LazyVerticalGrid(
				columns = GridCells.Adaptive(96.dp),
				modifier = Modifier.fillMaxWidth().height(220.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(20) { vIdx ->
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(40.dp)
							.background(if (vIdx % 2 == 0) vSecondary else vPrimary, RoundedCornerShape(6.dp))
					) {}
				}
			}
		}
	}
}
