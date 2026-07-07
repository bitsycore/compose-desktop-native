package screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ==================
// MARK: GraphicsLayerScreen
// ==================

/* Demonstrates Modifier.graphicsLayer: rotation / scale / alpha /
   translation, and the cacheKey opt-in that pre-renders a subtree into
   a texture and reuses it across frames.
   The same modifier API is implemented in both the Skia and SDL3
   renderers — Skia uses canvas transforms + raster surface caching,
   SDL3 uses SDL_RenderTextureRotated + per-node target textures. */
@Composable
internal fun GraphicsLayerScreen() {
	// A small animated rotation drives the "rotation" row each frame so
	// you can confirm the transform is live (vs being baked at first paint).
	var vSpin by remember { mutableStateOf(0f) }
	LaunchedEffect(Unit) {
		while (true) {
			vSpin = (vSpin + 2f) % 360f
			delay(16)
		}
	}

	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Modifier.graphicsLayer",
			"2D transform (scale / rotation / translation) + alpha + optional cacheKey opt-in. " +
				"When cacheKey is set, the renderer renders the subtree into an offscreen target once " +
				"and reuses it while the key compares equal — change the key to invalidate.",
		)

		Section(
			"Cached subtree (cacheKey)",
			"Identical to a fresh-drawn row visually, but the subtree is rendered once and reused while " +
				"cacheKey doesn't change. Useful for static screens — no per-frame redraw of the chrome.",
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vI in 0..3) {
					Box(
						modifier = Modifier
							.size(64.dp)
							.graphicsLayer(),
					) {
						Canvas(modifier = Modifier.size(64.dp)) {
							drawCircle(color = vPrimary, radius = 22f, center = Offset(32f, 32f))
							drawCircle(color = vSecondary, radius = 12f, center = Offset(32f, 32f))
						}
					}
				}
				Text("(cached)", color = vPrimary, fontSize = 12.sp)
			}
		}

		Section("Combined (cache + rotation)", "Cached subtree blitted with live rotation around its centre.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vI in 0..4) {
					Box(
						modifier = Modifier
							.size(64.dp)
							.graphicsLayer(
								rotationZ = vSpin + vI * 30f,
							),
					) {
						Canvas(modifier = Modifier.size(64.dp)) {
							drawRect(
								color = if (vI % 2 == 0) vPrimary else vSecondary,
								topLeft = Offset(10f, 10f),
								size = Size(44f, 44f),
							)
						}
					}
				}
			}
		}

		Section("Rotation (live)", "Each tile holds the same shape rotated at multiples of the current spin angle.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vI in 0..4) {
					Box(
						modifier = Modifier
							.size(64.dp)
							.graphicsLayer(rotationZ = vSpin + vI * 45f),
					) {
						Canvas(modifier = Modifier.size(64.dp)) {
							drawRect(color = vPrimary, topLeft = Offset(8f, 8f), size = Size(48f, 48f))
						}
					}
				}
			}
		}

		Section("Scale", "scaleX / scaleY with default pivot at the centre.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vS in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)) {
					Box(
						modifier = Modifier
							.size(64.dp)
							.graphicsLayer(scaleX = vS, scaleY = vS),
					) {
						Canvas(modifier = Modifier.size(64.dp)) {
							drawCircle(color = vSecondary, radius = 26f)
						}
					}
				}
			}
		}

		Section("Alpha", "Layer-level opacity — overlapping content fades as one unit.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				for (vA in listOf(1f, 0.75f, 0.5f, 0.25f, 0.1f)) {
					Box(
						modifier = Modifier
							.size(64.dp)
							.graphicsLayer(alpha = vA)
							.background(vPrimary),
					) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.background(vSecondary),
						) {}
					}
				}
			}
		}

	}
}
