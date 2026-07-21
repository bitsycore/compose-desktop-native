@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: PointerInputScreen — stock Compose gestures
// ==================

/** Stock Compose pointer-input patterns: detectTapGestures for taps / long
   press, detectDragGestures for drag, and an inline awaitPointerEventScope
   for the button variants (right / middle) upstream doesn't wrap in a
   named gesture detector. */
@Composable
internal fun PointerInputScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Pointer input", "detectTapGestures / detectDragGestures / awaitPointerEventScope.")

		Section("detectTapGestures", "onTap / onDoubleTap / onLongPress / onPress reported by androidx.compose.foundation.gestures.detectTapGestures.") {
			var vLast by remember { mutableStateOf("click, double-click, or long-press") }
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(64.dp)
					.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
					.pointerInput(Unit) {
						detectTapGestures(
							onTap = { vLast = "tap @ (${it.x.toInt()}, ${it.y.toInt()})" },
							onDoubleTap = { vLast = "double-tap @ (${it.x.toInt()}, ${it.y.toInt()})" },
							onLongPress = { vLast = "long press @ (${it.x.toInt()}, ${it.y.toInt()})" },
							onPress = { vLast = "press @ (${it.x.toInt()}, ${it.y.toInt()})" },
						)
					},
				contentAlignment = Alignment.Center,
			) {
				Text(vLast, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
			}
		}

		Section("Secondary / middle button", "awaitPointerEventScope + PointerEvent.button matching — upstream has no first-class right/middle-click gesture detector.") {
			var vSecondary by remember { mutableStateOf("right-click me") }
			var vMiddle by remember { mutableStateOf(0) }
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(64.dp)
					.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
					.pointerInput(Unit) {
						awaitPointerEventScope {
							while (true) {
								val vEv = awaitPointerEvent()
								if (vEv.type != PointerEventType.Press) continue
								val vCh = vEv.changes.firstOrNull() ?: continue
								when (vEv.button) {
									PointerButton.Secondary -> {
										vCh.consume()
										vSecondary = "secondary @ (${vCh.position.x.toInt()}, ${vCh.position.y.toInt()})"
									}
									PointerButton.Tertiary -> { vCh.consume(); vMiddle++ }
								}
							}
						}
					},
				contentAlignment = Alignment.Center,
			) {
				Text("$vSecondary · middle: $vMiddle", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
			}
		}

		Section("detectDragGestures", "onDragStart / onDrag (per-frame delta) / onDragEnd / onDragCancel from androidx.compose.foundation.gestures.detectDragGestures.") {
			var vOffsetX by remember { mutableStateOf(0f) }
			var vOffsetY by remember { mutableStateOf(0f) }
			var vLast by remember { mutableStateOf("drag the square") }
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(120.dp)
					.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp)),
			) {
				Box(
					modifier = Modifier
						.offset { IntOffset(vOffsetX.toInt(), vOffsetY.toInt()) }
						.size(56.dp)
						.background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
						.pointerInput(Unit) {
							detectDragGestures(
								onDragStart = { vLast = "drag start @ (${it.x.toInt()}, ${it.y.toInt()})" },
								onDrag = { _, delta -> vOffsetX += delta.x; vOffsetY += delta.y; vLast = "dx=${delta.x.toInt()}, dy=${delta.y.toInt()}" },
								onDragEnd = { vLast = "drag ended" },
								onDragCancel = { vLast = "drag cancelled" },
							)
						},
				)
			}
			Text(vLast, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 12.sp)
		}
	}
}
