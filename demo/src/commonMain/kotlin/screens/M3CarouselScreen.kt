@file:OptIn(ExperimentalMaterial3Api::class)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CarouselColors = listOf(
	Color(0xFF7C4DFF), Color(0xFF448AFF), Color(0xFF26A69A), Color(0xFFFFB300), Color(0xFFEF5350),
)

// Material3 — the carousel family (drag horizontally).
@Composable
internal fun M3CarouselScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Carousel", "MultiBrowse / Uncontained / CenteredHero horizontal carousels.")

		Section("HorizontalMultiBrowseCarousel", "Large + small items; drag to browse") {
			HorizontalMultiBrowseCarousel(
				state = rememberCarouselState { CarouselColors.size },
				preferredItemWidth = 180.dp,
				modifier = Modifier.fillMaxWidth().height(120.dp),
				itemSpacing = 8.dp,
			) { vIdx ->
				CarouselCard(vIdx)
			}
		}

		Section("HorizontalUncontainedCarousel", "Fixed-width items that overflow the edge") {
			HorizontalUncontainedCarousel(
				state = rememberCarouselState { CarouselColors.size },
				itemWidth = 140.dp,
				modifier = Modifier.fillMaxWidth().height(100.dp),
				itemSpacing = 8.dp,
			) { vIdx ->
				CarouselCard(vIdx)
			}
		}

		Section("HorizontalCenteredHeroCarousel", "One hero item centred, neighbours peeking") {
			HorizontalCenteredHeroCarousel(
				state = rememberCarouselState { CarouselColors.size },
				modifier = Modifier.fillMaxWidth().height(140.dp),
			) { vIdx ->
				CarouselCard(vIdx)
			}
		}
		Section("MultiAspectCarouselScope", "Scope entry point for lazy-list multi-aspect carousels (maskClip lives on it)") {
			androidx.compose.material3.carousel.MultiAspectCarouselScope {
				androidx.compose.foundation.lazy.LazyRow(
					modifier = Modifier.fillMaxWidth().height(80.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					items(CarouselColors.size) { vIdx ->
						Box(
							modifier = Modifier.width(120.dp).fillMaxHeight().background(CarouselColors[vIdx % CarouselColors.size]),
							contentAlignment = Alignment.Center,
						) {
							Text("Item $vIdx", color = MaterialTheme.colorScheme.onPrimary)
						}
					}
				}
			}
		}
	}
}

// One coloured carousel cell with its index.
@Composable
private fun CarouselCard(inIndex: Int) {
	Box(
		modifier = Modifier.fillMaxSize().background(CarouselColors[inIndex % CarouselColors.size]),
		contentAlignment = Alignment.Center,
	) {
		Text("Item $inIndex", color = MaterialTheme.colorScheme.onPrimary)
	}
}
