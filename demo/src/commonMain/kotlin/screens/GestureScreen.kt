package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: GestureScreen
// ==================

/* Exercises the new pointerInput + gesture DSL: detectTapGestures with
   onTap / onDoubleTap / onLongPress, and detectDragGestures driving a
   draggable square via per-frame deltas. */
@Composable
internal fun GestureScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary
	val vOnSurface = MaterialTheme.colorScheme.onSurface

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"pointerInput + gesture DSL",
			"Suspending Modifier.pointerInput { } with detectTapGestures / detectDragGestures " +
				"on top. Awaits resume on the SDL3FrameClock; no per-event polling.",
		)

		// ============
		//  detectTapGestures — onTap / onDoubleTap / onLongPress
		var vTapLog by remember { mutableStateOf("(no gesture yet)") }
		Section(
			"detectTapGestures",
			"Click once for onTap, twice quickly for onDoubleTap, hold for >500ms for onLongPress.",
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Box(
					modifier = Modifier
						.size(120.dp, 60.dp)
						.background(vPrimary, RoundedCornerShape(8.dp))
						.pointerInput(Unit) {
							detectTapGestures(
								onTap = { vTapLog = "onTap @ (${it.x.toInt()}, ${it.y.toInt()})" },
								onDoubleTap = { vTapLog = "onDoubleTap @ (${it.x.toInt()}, ${it.y.toInt()})" },
								onLongPress = { vTapLog = "onLongPress @ (${it.x.toInt()}, ${it.y.toInt()})" },
							)
						},
				) {
					Box(modifier = Modifier.padding(8.dp)) {
						Text("Tap me", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color(0xFF000000))
					}
				}
				Text(vTapLog, color = vOnSurface, fontSize = 14.sp)
			}
		}

		// ============
		//  detectDragGestures — drag a square around a parent box
		var vDx by remember { mutableStateOf(0f) }
		var vDy by remember { mutableStateOf(0f) }
		Section(
			"detectDragGestures",
			"Press and drag the square. Slop is 6px before the drag starts; the box reports per-frame deltas.",
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(200.dp)
					.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
			) {
				Box(
					modifier = Modifier
						.size(48.dp)
						.graphicsLayer(translationX = vDx, translationY = vDy)
						.background(vSecondary, RoundedCornerShape(8.dp))
						.pointerInput(Unit) {
							detectDragGestures(
								onDrag = { _, vDelta ->
									vDx += vDelta.x
									vDy += vDelta.y
								},
							)
						},
				) {}
			}
		}

		// ============
		//  Combination: tap to reset
		Section(
			"detectTapGestures + state combo",
			"Double-click the panel below to reset the draggable above.",
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(40.dp)
					.background(vPrimary, RoundedCornerShape(8.dp))
					.pointerInput(Unit) {
						detectTapGestures(onDoubleTap = {
							vDx = 0f; vDy = 0f
						})
					},
			) {
				Box(modifier = Modifier.padding(8.dp)) {
					Text("Double-click to reset", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color(0xFF000000))
				}
			}
		}
	}
}
