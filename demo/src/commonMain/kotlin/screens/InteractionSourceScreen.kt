package screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import demo.shim.demoPressable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: InteractionSourceScreen
// ==================

/* Demonstrates InteractionSource: a single source receives Press / Hover
   events from the existing pressable / hoverable modifiers, and visual
   feedback is wired off the source's state-backed booleans. Combined
   with animateColorAsState for smooth transitions. */
@Composable
internal fun InteractionSourceScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary
	val vSurface = MaterialTheme.colorScheme.surface
	val vOnSurface = MaterialTheme.colorScheme.onSurface

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"InteractionSource",
			"State-backed press / hover / focus stream. Compose components emit interactions; " +
				"call sites read isPressed / isHovered / isFocused (state-backed booleans) and " +
				"feed them into visual feedback like ripples or focus rings.",
		)

		// ============
		//  Single button with manual InteractionSource wiring
		Section("Press + Hover visual feedback", "Background transitions between idle / hovered / pressed.") {
			val vInter = remember { MutableInteractionSource() }
			val vPress by vInter.collectIsPressedAsState()
			val vHover by vInter.collectIsHoveredAsState()
			val vBg by animateColorAsState(when {
				vPress -> vSecondary
				vHover -> vPrimary
				else   -> vSurface
			})

			val vPressKey = remember { PressInteraction.Press(Offset.Zero) }
			val vHoverKey = remember { HoverInteraction.Enter() }
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Box(
					modifier = Modifier
						.size(120.dp, 48.dp)
						.background(vBg, RoundedCornerShape(8.dp))
						.hoverable(vInter)
						.demoPressable { vIn ->
							vInter.tryEmit(if (vIn) vPressKey else PressInteraction.Release(vPressKey))
						}
						.clickable {},
				) {
					Box(modifier = Modifier.padding(12.dp)) {
						Text("Hover/press me", color = vOnSurface, fontSize = 14.sp)
					}
				}
				Text(
					"isPressed = $vPress, isHovered = $vHover",
					color = vOnSurface,
					fontSize = 14.sp,
				)
			}
		}

		// ============
		//  Three buttons sharing one source (e.g. a tab group highlighting whichever is hovered).
		Section(
			"Multiple producers, one source",
			"All three buttons emit into the same InteractionSource. The source's isHovered " +
				"stays true while ANY of them is hovered (Enter counts are tracked).",
		) {
			val vInter = remember { MutableInteractionSource() }
			val vHover by vInter.collectIsHoveredAsState()
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				for (vI in 1..3) {
					val vKey = remember { HoverInteraction.Enter() }
					Box(
						modifier = Modifier
							.size(80.dp, 40.dp)
							.background(if (vHover) vPrimary else vSurface, RoundedCornerShape(6.dp))
							.hoverable(vInter),
					) {
						Box(modifier = Modifier.padding(8.dp)) {
							Text("btn $vI", color = vOnSurface, fontSize = 14.sp)
						}
					}
				}
			}
		}
	}
}
