package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.graphics.a8
import com.compose.desktop.native.graphics.b8
import com.compose.desktop.native.graphics.blend
import com.compose.desktop.native.graphics.darken
import com.compose.desktop.native.graphics.g8
import com.compose.desktop.native.graphics.lighten
import com.compose.desktop.native.graphics.r8
import com.compose.desktop.native.modifier.onDrag
import com.compose.desktop.native.modifier.onMiddleClick
import com.compose.desktop.native.modifier.onPressed
import com.compose.desktop.native.modifier.onSecondaryClick
import com.compose.desktop.native.modifier.onTextInput

// ==================
// MARK: Pointer / input modifiers + Color helpers (project-only)
// ==================

/* The project's non-official Modifier extensions (com.compose.desktop.native
   .modifier) that wrap SDL pointer / text events with no upstream analog, plus
   the Color blend / lighten / darken / r8..a8 helpers. */
@Composable
internal fun PointerInputScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Pointer input & Color", "onSecondaryClick / onMiddleClick / onPressed / onDrag / onTextInput + Color helpers.")

		Section("onSecondaryClick / onMiddleClick", "Right-click reports coordinates; middle-click increments a counter") {
			var secondary by remember { mutableStateOf("right-click the box") }
			var middle by remember { mutableStateOf(0) }
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(64.dp)
					.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
					.onSecondaryClick { x, y -> secondary = "secondary @ ($x, $y)" }
					.onMiddleClick { middle++ },
				contentAlignment = Alignment.Center,
			) {
				Text("$secondary · middle-clicks: $middle", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
			}
		}

		Section("onPressed", "Reports press coordinates relative to the node's top-left") {
			var pressed by remember { mutableStateOf("press the box") }
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(64.dp)
					.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
					.onPressed { x, y -> pressed = "pressed @ ($x, $y)" },
				contentAlignment = Alignment.Center,
			) {
				Text(pressed, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
			}
		}

		Section("onDrag", "Start / move / end drag callbacks with press-relative coordinates") {
			var drag by remember { mutableStateOf("drag across the box") }
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(64.dp)
					.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
					.onDrag(
						onStart = { x, y -> drag = "drag start @ ($x, $y)" },
						onDrag = { x, y -> drag = "dragging @ ($x, $y)" },
						onEnd = { drag = "drag ended" },
					),
				contentAlignment = Alignment.Center,
			) {
				Text(drag, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 13.sp)
			}
		}

		Section("onTextInput", "Receives IME-committed text while focused — click Focus, then type") {
			var typed by remember { mutableStateOf("") }
			val focus = remember { FocusRequester() }
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Button(onClick = { focus.requestFocus() }) { Text("Focus & type") }
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(56.dp)
						.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
						.focusRequester(focus)
						.focusable()
						.onTextInput { typed += it }
						.padding(12.dp),
					contentAlignment = Alignment.CenterStart,
				) {
					Text(if (typed.isEmpty()) "(focused input target)" else typed, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
				}
			}
		}

		Section("Color helpers", "blend / lighten / darken and the 8-bit r8 / g8 / b8 / a8 channel accessors") {
			val base = MaterialTheme.colorScheme.primary
			val other = MaterialTheme.colorScheme.tertiary
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				Swatch("base", base)
				Swatch("light", base.lighten(0.4f))
				Swatch("dark", base.darken(0.4f))
				Swatch("blend", base.blend(other, 0.5f))
			}
			Text(
				"base rgba8 = (${base.r8}, ${base.g8}, ${base.b8}, ${base.a8})",
				color = MaterialTheme.colorScheme.onSurface,
				fontSize = 12.sp,
			)
		}
	}
}
