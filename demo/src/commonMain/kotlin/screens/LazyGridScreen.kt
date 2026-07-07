package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Foundation / lazy — LazyVerticalGrid + LazyRow. Both are nested inside the
// screen's own verticalScroll, so each is given a bounded size (a lazy list with
// unbounded main-axis space would fail to measure).
@Composable
internal fun LazyGridScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Lazy Grid & Row",
			"androidx.compose.foundation.lazy — LazyVerticalGrid (fixed columns) and a horizontally-scrolling LazyRow.",
		)

		val vColors = listOf(
			Color(0xFF7C4DFF), Color(0xFF18FFFF), Color(0xFF69F0AE), Color(0xFFFFD54F),
			Color(0xFFEC407A), Color(0xFF29B6F6), Color(0xFFAB47BC), Color(0xFFFF7043),
		)

		Section("LazyVerticalGrid", "GridCells.Fixed(4) — 24 cells, only visible rows composed") {
			Box(Modifier.fillMaxWidth().height(260.dp)) {
				LazyVerticalGrid(
					columns = GridCells.Fixed(4),
					modifier = Modifier.fillMaxSize(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
					contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
				) {
					items(24) { vIndex ->
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.aspectRatio(1f)
								.background(vColors[vIndex % vColors.size], RoundedCornerShape(10.dp)),
							contentAlignment = Alignment.Center,
						) {
							Text("$vIndex", color = Color.Black, fontSize = 14.sp)
						}
					}
				}
			}
		}

		Section("LazyRow", "Horizontally-scrolling row of 30 chips") {
			LazyRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
			) {
				items(30) { vIndex ->
					Box(
						modifier = Modifier
							.size(width = 96.dp, height = 64.dp)
							.background(vColors[vIndex % vColors.size].copy(alpha = 0.85f), RoundedCornerShape(12.dp))
							.padding(10.dp),
						contentAlignment = Alignment.BottomStart,
					) {
						Text("Item $vIndex", color = Color.Black, fontSize = 13.sp)
					}
				}
			}
		}
	}
}
