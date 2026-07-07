package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PageColors = listOf(
	Color(0xFF5E35B1), Color(0xFF1E88E5), Color(0xFF00897B), Color(0xFFF4511E),
)

// Foundation — HorizontalPager / VerticalPager (drag to page).
@Composable
internal fun PagerScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Pager", "foundation.pager — drag pages horizontally / vertically, snap per page.")

		Section("HorizontalPager", "One full-width page at a time") {
			val vState = rememberPagerState { PageColors.size }
			HorizontalPager(state = vState, modifier = Modifier.fillMaxWidth().height(140.dp)) { vPage ->
				PageCell(vPage)
			}
			Text(
				"page ${vState.currentPage + 1} / ${PageColors.size}",
				color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
				fontSize = 12.sp,
			)
		}

		Section("VerticalPager", "Same mechanics, vertical axis") {
			val vState = rememberPagerState { PageColors.size }
			VerticalPager(state = vState, modifier = Modifier.fillMaxWidth().height(160.dp)) { vPage ->
				PageCell(vPage)
			}
		}
	}
}

// One coloured page with its index.
@Composable
private fun PageCell(inPage: Int) {
	Box(
		modifier = Modifier.fillMaxSize().padding(2.dp).background(PageColors[inPage % PageColors.size]),
		contentAlignment = Alignment.Center,
	) {
		Text("Page ${inPage + 1}", color = Color.White, fontSize = 18.sp)
	}
}
