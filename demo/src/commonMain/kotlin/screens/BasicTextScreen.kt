@file:OptIn(ExperimentalFoundationApi::class)

package screens

import androidx.compose.foundation.BasicTooltipBox
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberBasicTooltipState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider

// Foundation — the undecorated text primitive below material3's Text, plus the
// foundation selection + tooltip plumbing. The companion BasicTextField screen
// covers editable text.
@Composable
internal fun BasicTextScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("BasicText", "BasicText, SelectionContainer / DisableSelection, BasicTooltipBox.")

		Section("BasicText", "The undecorated foundation text primitive — style passed explicitly") {
			BasicText(
				"BasicText — no material styling, style passed explicitly.",
				style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
			)
		}

		Section("SelectionContainer / DisableSelection", "Drag-select the first and last lines; the middle is opted out") {
			SelectionContainer {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text("Selectable: drag across this line.")
					DisableSelection {
						Text("Not selectable: DisableSelection wraps this line.")
					}
					Text("Selectable again below the disabled block.")
				}
			}
		}

		Section("BasicTooltipBox", "Foundation tooltip plumbing — hover the button") {
			BasicTooltipBox(
				positionProvider = AboveAnchor,
				state = rememberBasicTooltipState(),
				tooltip = {
					Box(
						modifier = Modifier
							.background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(4.dp))
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Text("BasicTooltip", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp)
					}
				},
			) {
				Button(onClick = {}) { Text("Hover me") }
			}
		}
	}
}

// Places the tooltip centred above its anchor with a 4px gap.
private val AboveAnchor = object : PopupPositionProvider {
	override fun calculatePosition(
		anchorBounds: IntRect,
		windowSize: IntSize,
		layoutDirection: LayoutDirection,
		popupContentSize: IntSize,
	): IntOffset = IntOffset(
		x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
		y = anchorBounds.top - popupContentSize.height - 4,
	)
}
