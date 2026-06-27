package androidx.compose.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// ==================
// MARK: FocusInteraction
// ==================

/* Focus interactions for a component. Focus starts focus; Unfocus ends the
   Focus it references. */
interface FocusInteraction : Interaction {
	class Focus : FocusInteraction
	class Unfocus(val focus: Focus) : FocusInteraction
}

/* Collects whether this source is focused into snapshot State by folding its
   interactions Flow (a Focus with no matching Unfocus). */
@Composable
fun InteractionSource.collectIsFocusedAsState(): State<Boolean> {
	val vIsFocused = remember { mutableStateOf(false) }
	LaunchedEffect(this) {
		val vFocuses = mutableListOf<FocusInteraction.Focus>()
		interactions.collect { vInteraction ->
			when (vInteraction) {
				is FocusInteraction.Focus   -> vFocuses.add(vInteraction)
				is FocusInteraction.Unfocus -> vFocuses.remove(vInteraction.focus)
			}
			vIsFocused.value = vFocuses.isNotEmpty()
		}
	}
	return vIsFocused
}
