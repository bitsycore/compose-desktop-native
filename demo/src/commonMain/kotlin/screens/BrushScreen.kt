package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Foundation / ui.graphics — Brush gradients painted through Modifier.background.
// Exercises the SDL renderer's per-vertex gradient sampler (linear / radial / sweep).
@Composable
internal fun BrushScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Brushes & Gradients",
			"androidx.compose.ui.graphics.Brush — linear / horizontal / vertical / radial / sweep, painted via Modifier.background.",
		)

		val vStops = listOf(Color(0xFF7C4DFF), Color(0xFF18FFFF), Color(0xFF69F0AE))

		Section("Linear / Horizontal / Vertical", "Brush.linearGradient / horizontalGradient / verticalGradient") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				GradientSwatch("linear", Brush.linearGradient(vStops))
				GradientSwatch("horizontal", Brush.horizontalGradient(vStops))
				GradientSwatch("vertical", Brush.verticalGradient(vStops))
			}
		}

		Section("Radial / Sweep", "Brush.radialGradient / sweepGradient") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				GradientSwatch("radial", Brush.radialGradient(vStops))
				GradientSwatch("sweep", Brush.sweepGradient(vStops))
			}
		}

		Section("Two-stop ramps", "Start → end ramps, the kind used for headers and call-to-action fills") {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				Box(
					Modifier.fillMaxWidth().height(48.dp).background(
						Brush.horizontalGradient(listOf(Color(0xFFEC407A), Color(0xFFAB47BC))),
						RoundedCornerShape(10.dp),
					),
				)
				Box(
					Modifier.fillMaxWidth().height(48.dp).background(
						Brush.horizontalGradient(listOf(Color(0xFF29B6F6), Color(0xFF66BB6A))),
						RoundedCornerShape(10.dp),
					),
				)
			}
		}
	}
}

@Composable
private fun GradientSwatch(label: String, brush: Brush) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(Modifier.size(96.dp).background(brush, RoundedCornerShape(12.dp)))
		Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
	}
}
