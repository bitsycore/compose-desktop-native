package androidx.compose.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// ==================
// MARK: HoverInteraction
// ==================

/* Hover interactions for a component. Enter starts a hover; Exit ends the
   Enter it references. */
interface HoverInteraction : Interaction {
	class Enter : HoverInteraction
	class Exit(val enter: Enter) : HoverInteraction
}

/* Collects whether this source is hovered into snapshot State by folding its
   interactions Flow (an Enter with no matching Exit). */
@Composable
fun InteractionSource.collectIsHoveredAsState(): State<Boolean> {
	val vIsHovered = remember { mutableStateOf(false) }
	LaunchedEffect(this) {
		val vEnters = mutableListOf<HoverInteraction.Enter>()
		interactions.collect { vInteraction ->
			when (vInteraction) {
				is HoverInteraction.Enter -> vEnters.add(vInteraction)
				is HoverInteraction.Exit  -> vEnters.remove(vInteraction.enter)
			}
			vIsHovered.value = vEnters.isNotEmpty()
		}
	}
	return vIsHovered
}
