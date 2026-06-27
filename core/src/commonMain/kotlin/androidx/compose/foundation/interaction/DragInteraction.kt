package androidx.compose.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// ==================
// MARK: DragInteraction
// ==================

/* Drag interactions for a component. Start begins a drag; Stop / Cancel end
   the Start they reference. */
interface DragInteraction : Interaction {
	class Start : DragInteraction
	class Stop(val start: Start) : DragInteraction
	class Cancel(val start: Start) : DragInteraction
}

/* Collects whether this source is dragged into snapshot State by folding its
   interactions Flow (a Start with no matching Stop / Cancel). */
@Composable
fun InteractionSource.collectIsDraggedAsState(): State<Boolean> {
	val vIsDragged = remember { mutableStateOf(false) }
	LaunchedEffect(this) {
		val vStarts = mutableListOf<DragInteraction.Start>()
		interactions.collect { vInteraction ->
			when (vInteraction) {
				is DragInteraction.Start  -> vStarts.add(vInteraction)
				is DragInteraction.Stop   -> vStarts.remove(vInteraction.start)
				is DragInteraction.Cancel -> vStarts.remove(vInteraction.start)
			}
			vIsDragged.value = vStarts.isNotEmpty()
		}
	}
	return vIsDragged
}
