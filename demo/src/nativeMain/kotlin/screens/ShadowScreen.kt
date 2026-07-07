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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Foundation — Modifier.shadow: elevation ladder, shapes, tints, backdrops.
@Composable
internal fun ShadowScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Shadows", "Modifier.shadow — SDL renders stacked-ring falloff, Skia a Gaussian blur.")

		Section("Elevation ladder", "1 → 24 dp on a light backdrop (shadows read best on light)") {
			LightBackdrop {
				Row(
					horizontalArrangement = Arrangement.spacedBy(26.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					for (vElevation in listOf(1, 2, 4, 8, 12, 16, 24)) {
						ShadowChip(elevation = vElevation.dp, label = "${vElevation}dp")
					}
				}
			}
		}

		Section("Shapes", "The shadow follows the layer's shape outline") {
			LightBackdrop {
				Row(
					horizontalArrangement = Arrangement.spacedBy(30.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					ShadowChip(8.dp, "rect", RoundedCornerShape(0.dp))
					ShadowChip(8.dp, "round", RoundedCornerShape(14.dp))
					ShadowChip(8.dp, "circle", CircleShape)
					ShadowChip(8.dp, "cut", CutCornerShape(14.dp))
				}
			}
		}

		Section("Tinted shadows", "ambientColor / spotColor tint the falloff") {
			LightBackdrop {
				Row(
					horizontalArrangement = Arrangement.spacedBy(30.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					for ((vLabel, vTint) in listOf(
						"black" to Color.Black,
						"red" to Color(0xFFD32F2F),
						"blue" to Color(0xFF1565C0),
						"green" to Color(0xFF2E7D32),
					)) {
						Box(
							modifier = Modifier
								.shadow(
									elevation = 10.dp,
									shape = RoundedCornerShape(12.dp),
									ambientColor = vTint,
									spotColor = vTint,
								)
								.size(64.dp)
								.background(Color.White, RoundedCornerShape(12.dp)),
							contentAlignment = Alignment.Center,
						) {
							Text(vLabel, color = Color(0xFF333333), fontSize = 11.sp)
						}
					}
				}
			}
		}

		Section("Backdrops", "Same 8dp shadow over light, mid, and dark surfaces") {
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				for (vBackdrop in listOf(Color(0xFFF2F2F5), Color(0xFF8A8A92), Color(0xFF1A1A1E))) {
					Box(
						modifier = Modifier
							.weight(1f)
							.height(110.dp)
							.background(vBackdrop, RoundedCornerShape(8.dp)),
						contentAlignment = Alignment.Center,
					) {
						Box(
							modifier = Modifier
								.shadow(8.dp, RoundedCornerShape(10.dp))
								.size(56.dp)
								.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
						)
					}
				}
			}
		}

		Section("Interactive", "Drag the slider — elevation animates the blur + offset live") {
			var vElevation by remember { mutableStateOf(6f) }
			LightBackdrop(height = 150.dp) {
				Box(
					modifier = Modifier
						.shadow(vElevation.dp, RoundedCornerShape(16.dp))
						.size(96.dp)
						.background(Color.White, RoundedCornerShape(16.dp)),
					contentAlignment = Alignment.Center,
				) {
					Text("${vElevation.toInt()}dp", color = Color(0xFF333333), fontSize = 13.sp)
				}
			}
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Slider(
					value = vElevation,
					onValueChange = { vElevation = it },
					valueRange = 0f..24f,
					modifier = Modifier.width(320.dp),
				)
				Text("${vElevation.toInt()} dp", fontSize = 13.sp)
			}
		}
	}
}

// Light panel the shadow samples sit on — dark-theme backgrounds swallow
// shadows, so most sections stage on this.
@Composable
private fun LightBackdrop(height: Dp = 120.dp, content: @Composable () -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(height)
			.background(Color(0xFFF2F2F5), RoundedCornerShape(8.dp))
			.padding(horizontal = 20.dp),
		contentAlignment = Alignment.CenterStart,
	) {
		content()
	}
}

// One white chip with the given shadow elevation + shape.
@Composable
private fun ShadowChip(elevation: Dp, label: String, shape: Shape = RoundedCornerShape(10.dp)) {
	Box(
		modifier = Modifier
			.shadow(elevation, shape)
			.size(60.dp)
			.background(Color.White, shape),
		contentAlignment = Alignment.Center,
	) {
		Text(label, color = Color(0xFF333333), fontSize = 11.sp)
	}
}
