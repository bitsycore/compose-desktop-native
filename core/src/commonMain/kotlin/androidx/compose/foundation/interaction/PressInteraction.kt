package androidx.compose.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

// ==================
// MARK: PressInteraction
// ==================

/* Press interactions for a component. Press starts a press at pressPosition;
   Release / Cancel end the press they reference. */
interface PressInteraction : Interaction {
	class Press(val pressPosition: Offset) : PressInteraction
	class Release(val press: Press) : PressInteraction
	class Cancel(val press: Press) : PressInteraction
}

/* Collects whether this source is pressed into snapshot State by folding its
   interactions Flow (a Press with no matching Release / Cancel). */
@Composable
fun InteractionSource.collectIsPressedAsState(): State<Boolean> {
	val vIsPressed = remember { mutableStateOf(false) }
	LaunchedEffect(this) {
		val vPresses = mutableListOf<PressInteraction.Press>()
		interactions.collect { vInteraction ->
			when (vInteraction) {
				is PressInteraction.Press   -> vPresses.add(vInteraction)
				is PressInteraction.Release -> vPresses.remove(vInteraction.press)
				is PressInteraction.Cancel  -> vPresses.remove(vInteraction.press)
			}
			vIsPressed.value = vPresses.isNotEmpty()
		}
	}
	return vIsPressed
}
