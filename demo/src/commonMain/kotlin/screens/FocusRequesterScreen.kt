package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: FocusRequesterScreen
// ==================

/* Programmatic focus via FocusRequester + LocalFocusManager. Two
   focusable boxes; buttons in the right column move focus to either
   one, or clear focus globally. */
@Composable
internal fun FocusRequesterScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary
	val vSurface = MaterialTheme.colorScheme.surface
	val vOnSurface = MaterialTheme.colorScheme.onSurface
	val vFocusManager = LocalFocusManager.current

	val vReqA = remember { FocusRequester() }
	val vReqB = remember { FocusRequester() }
	var vFocusedA by remember { mutableStateOf(false) }
	var vFocusedB by remember { mutableStateOf(false) }

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"FocusRequester + FocusManager",
			"Programmatic focus: bind a FocusRequester to a focusable, call .requestFocus() to " +
				"move focus there. LocalFocusManager.clearFocus() drops focus entirely.",
		)

		Section("Two focusable targets", "The focused one is highlighted via onFocusChanged.") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Box(
					modifier = Modifier
						.size(120.dp, 60.dp)
						.background(if (vFocusedA) vSecondary else vSurface, RoundedCornerShape(8.dp))
						.focusRequester(vReqA)
						.onFocusChanged { vFocusedA = it.isFocused }.focusable(),
				) {
					Box(modifier = Modifier.padding(12.dp)) {
						Text("Target A (${if (vFocusedA) "focused" else "idle"})", color = vOnSurface, fontSize = 14.sp)
					}
				}
				Box(
					modifier = Modifier
						.size(120.dp, 60.dp)
						.background(if (vFocusedB) vSecondary else vSurface, RoundedCornerShape(8.dp))
						.focusRequester(vReqB)
						.onFocusChanged { vFocusedB = it.isFocused }.focusable(),
				) {
					Box(modifier = Modifier.padding(12.dp)) {
						Text("Target B (${if (vFocusedB) "focused" else "idle"})", color = vOnSurface, fontSize = 14.sp)
					}
				}
			}
		}

		Section("Buttons", "Move focus or clear it.") {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Box(modifier = Modifier
					.size(96.dp, 40.dp)
					.background(vPrimary, RoundedCornerShape(6.dp))
					.clickable { vReqA.requestFocus() }
				) {
					Box(modifier = Modifier.padding(8.dp)) {
						Text("Focus A", color = androidx.compose.ui.graphics.Color(0xFF000000), fontSize = 14.sp)
					}
				}
				Box(modifier = Modifier
					.size(96.dp, 40.dp)
					.background(vPrimary, RoundedCornerShape(6.dp))
					.clickable { vReqB.requestFocus() }
				) {
					Box(modifier = Modifier.padding(8.dp)) {
						Text("Focus B", color = androidx.compose.ui.graphics.Color(0xFF000000), fontSize = 14.sp)
					}
				}
				Box(modifier = Modifier
					.size(96.dp, 40.dp)
					.background(vSecondary, RoundedCornerShape(6.dp))
					.clickable { vFocusManager?.clearFocus() }
				) {
					Box(modifier = Modifier.padding(8.dp)) {
						Text("Clear", color = androidx.compose.ui.graphics.Color(0xFF000000), fontSize = 14.sp)
					}
				}
			}
		}

		// ============
		//  onFocusChanged standalone — observes focus without itself being focusable
		var vObserved by remember { mutableStateOf("idle") }
		Section("onFocusChanged standalone", "Wraps a focusable child; the observed text updates without making the wrapper focusable.") {
			Box(
				modifier = Modifier
					.onFocusChanged { vObserved = if (it.isFocused) "focused" else "idle" },
			) {
				Box(modifier = Modifier.padding(4.dp)) {
					Text("Observed: $vObserved", color = vOnSurface, fontSize = 14.sp)
				}
			}
		}
	}
}
