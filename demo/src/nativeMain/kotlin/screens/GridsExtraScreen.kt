package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CellColors = listOf(
	Color(0xFF7E57C2), Color(0xFF42A5F5), Color(0xFF26A69A),
	Color(0xFFFFCA28), Color(0xFFEC407A), Color(0xFF66BB6A),
)

// Foundation — the lazy grid variants beyond LazyVerticalGrid.
@Composable
internal fun GridsExtraScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Grids extra", "LazyHorizontalGrid + vertical / horizontal staggered grids.")

		Section("LazyHorizontalGrid", "Rows fixed at 2, scrolls horizontally") {
			LazyHorizontalGrid(
				rows = GridCells.Fixed(2),
				modifier = Modifier.fillMaxWidth().height(120.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				items(20) { vIdx -> GridCell(vIdx, 90.dp) }
			}
		}

		Section("LazyVerticalStaggeredGrid", "Masonry columns — item heights vary") {
			LazyVerticalStaggeredGrid(
				columns = StaggeredGridCells.Fixed(3),
				modifier = Modifier.fillMaxWidth().height(240.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalItemSpacing = 6.dp,
			) {
				items(21) { vIdx -> GridCell(vIdx, (50 + (vIdx * 37) % 70).dp, fillWidth = true) }
			}
		}

		Section("LazyHorizontalStaggeredGrid", "Masonry rows — item widths vary") {
			LazyHorizontalStaggeredGrid(
				rows = StaggeredGridCells.Fixed(2),
				modifier = Modifier.fillMaxWidth().height(120.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
				horizontalItemSpacing = 6.dp,
			) {
				items(20) { vIdx -> GridCell(vIdx, 52.dp, width = (60 + (vIdx * 41) % 80).dp) }
			}
		}
	}
}

// One coloured, labelled grid cell.
@Composable
private fun GridCell(
	inIndex: Int,
	inHeight: androidx.compose.ui.unit.Dp,
	fillWidth: Boolean = false,
	width: androidx.compose.ui.unit.Dp = 90.dp,
) {
	val vBase = Modifier
		.background(CellColors[inIndex % CellColors.size], RoundedCornerShape(6.dp))
	val vSized = if (fillWidth) vBase.fillMaxWidth().height(inHeight) else vBase.size(width, inHeight)
	Box(modifier = vSized, contentAlignment = Alignment.Center) {
		Text("$inIndex", color = Color.White)
	}
}
