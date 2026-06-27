package androidx.compose.foundation.interaction

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// ==================
// MARK: Interaction
// ==================

/* An interaction emitted by a component on its InteractionSource (press /
   hover / focus / drag). Deliberately a plain (non-sealed) interface so new
   interaction families can be added without touching this one — matches
   official androidx.compose.foundation.interaction.Interaction. */
interface Interaction

// ==================
// MARK: InteractionSource
// ==================

/* Read-only stream of the interactions emitted for a component. Collectors
   (collectIsPressedAsState / collectIsHoveredAsState / ...) observe this Flow
   and fold it into snapshot state. */
interface InteractionSource {
	val interactions: Flow<Interaction>
}

/* Writable InteractionSource. Producers (clickable, focusable, ...) emit
   interactions; the read side collects them via the collectIs*AsState helpers. */
interface MutableInteractionSource : InteractionSource {
	suspend fun emit(interaction: Interaction)
	fun tryEmit(interaction: Interaction): Boolean
}

// ==================
// MARK: factory
// ==================

/* Official top-level factory for a MutableInteractionSource. (The project's
   rememberMutableInteractionSource() convenience lives in
   com.compose.desktop.native.modifier.) */
fun MutableInteractionSource(): MutableInteractionSource = MutableInteractionSourceImpl()

private class MutableInteractionSourceImpl : MutableInteractionSource {
	override val interactions = MutableSharedFlow<Interaction>(
		extraBufferCapacity = 16,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)

	override suspend fun emit(interaction: Interaction) {
		interactions.emit(interaction)
	}

	override fun tryEmit(interaction: Interaction): Boolean =
		interactions.tryEmit(interaction)
}
