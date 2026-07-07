@file:OptIn(ExperimentalLayoutApi::class)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val FlowWords = listOf(
	"Compose", "SDL3", "Kotlin", "Native", "FreeType", "Skia", "Vulkan", "Metal",
	"OpenGL", "Windows", "macOS", "Linux", "Wayland", "X11",
)

// Foundation — FlowRow / FlowColumn wrap their children onto new lines/columns.
@Composable
internal fun FlowLayoutScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Flow layout", "FlowRow wraps to new lines; FlowColumn wraps to new columns.")

		Section("FlowRow", "Chips wrap when the row runs out of width") {
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				for (vWord in FlowWords) FlowChip(vWord)
			}
		}

		Section("FlowColumn", "Fills a column, then wraps to the next one") {
			FlowColumn(
				modifier = Modifier.fillMaxWidth().height(150.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				for (vWord in FlowWords.take(10)) FlowChip(vWord)
			}
		}
	}
}

// A tiny tonal pill used by both flow samples.
@Composable
private fun FlowChip(inLabel: String) {
	Box(
		modifier = Modifier
			.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
			.padding(horizontal = 12.dp, vertical = 6.dp),
	) {
		Text(inLabel, color = MaterialTheme.colorScheme.onSecondaryContainer)
	}
}
