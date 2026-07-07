package screens
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ==================
// MARK: ModifierShortcutsScreen
// ==================

/* Thin one-line modifiers built on top of Modifier.graphicsLayer:
   rotate / scale / translate, plus zIndex which reorders siblings at
   draw time. */
@Composable
internal fun ModifierShortcutsScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Modifier shortcuts",
			"rotate / scale / translate (wrappers over graphicsLayer) + zIndex (renderer sorts " +
				"siblings by z before drawing).",
		)

		Section("Modifier.rotate", "0°, 15°, 30°, 45°, 60° around centre.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vAngle in listOf(0f, 15f, 30f, 45f, 60f)) {
					Box(modifier = Modifier
						.size(64.dp)
						.rotate(vAngle)
						.background(vPrimary, RoundedCornerShape(8.dp))
					) {}
				}
			}
		}

		Section("Modifier.scale", "0.5x, 0.75x, 1x, 1.25x, 1.5x uniform.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vS in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)) {
					Box(modifier = Modifier
						.size(64.dp)
						.scale(vS)
						.background(vSecondary, RoundedCornerShape(8.dp))
					) {}
				}
			}
		}

		Section("Modifier.translate", "Two boxes nudged from their layout position without moving the parent.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Box(modifier = Modifier
					.size(64.dp)
					.background(vPrimary, RoundedCornerShape(8.dp))
				) {}
				Box(modifier = Modifier
					.size(64.dp)
					.graphicsLayer(translationX = 20f, translationY = -10f)
					.background(vSecondary, RoundedCornerShape(8.dp))
				) {}
				Box(modifier = Modifier
					.size(64.dp)
					.graphicsLayer(translationX = -10f, translationY = 20f)
					.background(vPrimary, RoundedCornerShape(8.dp))
				) {}
			}
		}

		Section(
			"Modifier.zIndex",
			"Three overlapping boxes in tree order purple → cyan → purple, but zIndex pushes the " +
				"middle one (z=2) to the front.",
		) {
			Box(modifier = Modifier.size(160.dp)) {
				Box(modifier = Modifier
					.size(80.dp)
					.graphicsLayer(translationX = 0f, translationY = 0f)
					.background(vPrimary, RoundedCornerShape(8.dp))
				) {}
				Box(modifier = Modifier
					.size(80.dp)
					.graphicsLayer(translationX = 40f, translationY = 20f)
					.zIndex(2f)
					.background(vSecondary, RoundedCornerShape(8.dp))
				) {}
				Box(modifier = Modifier
					.size(80.dp)
					.graphicsLayer(translationX = 80f, translationY = 40f)
					.background(vPrimary, RoundedCornerShape(8.dp))
				) {}
			}
		}
	}
}
