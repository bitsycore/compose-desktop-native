package androidx.compose.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// ==================
// MARK: Interaction
// ==================

/* Lifecycle events emitted by a component on its InteractionSource. We
   model the subset that's actually rendered as visual feedback today:
   press / hover / focus, each with a Start and End event so an indication
   can hold visible-while-active state. Drag and selection events that
   upstream provides are absent — easy to add when needed. */
sealed interface Interaction

object PressInteraction {
	class Press(val position: androidx.compose.ui.geometry.Offset) : Interaction
	class Release(val press: Press) : Interaction
	class Cancel(val press: Press) : Interaction
}

object HoverInteraction {
	class Enter : Interaction
	class Exit(val enter: Enter) : Interaction
}

object FocusInteraction {
	class Focus : Interaction
	class Unfocus(val focus: Focus) : Interaction
}

// ==================
// MARK: InteractionSource
// ==================

/* Read-only view of a component's interaction stream. State-backed
   booleans (`collectIsPressedAsState` etc.) read directly from
   snapshot state rather than a Flow — simpler than the upstream Flow
   approach and good enough for the visual-feedback use case. */
interface InteractionSource {

	/* True while at least one Press is active (no matching Release / Cancel). */
	val isPressed: Boolean

	/* True while a HoverInteraction.Enter has not been balanced by an Exit. */
	val isHovered: Boolean

	/* True while focused. */
	val isFocused: Boolean
}

/* Writable InteractionSource. Producers (clickable, focusable, ...) emit
   interactions here; the InteractionSource side reads the resulting
   isPressed / isHovered / isFocused state. */
interface MutableInteractionSource : InteractionSource {

	fun tryEmit(interaction: Interaction)
}

// ==================
// MARK: factory
// ==================

/* Official top-level factory for a MutableInteractionSource. (The project's
   rememberMutableInteractionSource() convenience lives in
   com.compose.desktop.native.modifier.) */
fun MutableInteractionSource(): MutableInteractionSource = MutableInteractionSourceImpl()

private class MutableInteractionSourceImpl : MutableInteractionSource {
	private var fPressCount: Int by mutableStateOf(0)
	private var fHoverCount: Int by mutableStateOf(0)
	private var fFocused: Boolean by mutableStateOf(false)

	override val isPressed: Boolean get() = fPressCount > 0
	override val isHovered: Boolean get() = fHoverCount > 0
	override val isFocused: Boolean get() = fFocused

	override fun tryEmit(interaction: Interaction) {
		when (interaction) {
			is PressInteraction.Press   -> fPressCount += 1
			is PressInteraction.Release -> fPressCount = (fPressCount - 1).coerceAtLeast(0)
			is PressInteraction.Cancel  -> fPressCount = (fPressCount - 1).coerceAtLeast(0)
			is HoverInteraction.Enter   -> fHoverCount += 1
			is HoverInteraction.Exit    -> fHoverCount = (fHoverCount - 1).coerceAtLeast(0)
			is FocusInteraction.Focus   -> fFocused = true
			is FocusInteraction.Unfocus -> fFocused = false
		}
	}
}

// ==================
// MARK: State-collector helpers (upstream-shape composables)
// ==================

@Composable
fun InteractionSource.collectIsPressedAsState(): State<Boolean> =
	stateOf(isPressed)

@Composable
fun InteractionSource.collectIsHoveredAsState(): State<Boolean> =
	stateOf(isHovered)

@Composable
fun InteractionSource.collectIsFocusedAsState(): State<Boolean> =
	stateOf(isFocused)

/* Wrap a snapshot-state Boolean read in a State<Boolean> so call sites
   feel like upstream. The lambda is re-evaluated on each recomposition
   that this State is read in, which is what we want. */
@Composable
private fun stateOf(inValue: Boolean): State<Boolean> {
	val vState = remember { mutableStateOf(inValue) }
	vState.value = inValue
	return vState
}
