package androidx.compose.ui.input.pointer

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException

// ==================
// MARK: PointerInputChange
// ==================

/* One pointer's per-frame snapshot — position now and previously, plus
   pressed bits so handlers can detect transitions (press, release,
   move-while-down). `id` is the pointer index (mouse = 0; touch
   contacts later may carry distinct ids). `consume()` marks the change
   handled so ancestor pointerInput blocks ignore it on subsequent
   passes. */
class PointerInputChange(
	val id: Long,
	val position: Offset,
	val pressed: Boolean,
	val previousPosition: Offset,
	val previousPressed: Boolean,
) {
	var consumed: Boolean = false; private set
	fun consume() { consumed = true }
}

// ==================
// MARK: PointerEvent (rich)
// ==================

/* Suspending DSL view of a pointer event: a list of changes (we ship one
   change per event today; multi-touch can extend later). Distinct from
   the simpler `androidx.compose.ui.input.pointer.PointerEvent` that
   ComposeWindow uses internally for legacy dispatch. */
class PointerInputEvent(val changes: List<PointerInputChange>)

// ==================
// MARK: Scopes
// ==================

/* Scope passed to the suspending block of Modifier.pointerInput. Inside
   you typically open an `awaitPointerEventScope { ... }` and loop on
   `awaitPointerEvent()`. */
interface PointerInputScope {

	suspend fun <R> awaitPointerEventScope(
		inBlock: suspend AwaitPointerEventScope.() -> R,
	): R
}

/* The scope inside awaitPointerEventScope where you can suspend on
   pointer events. */
interface AwaitPointerEventScope {

	suspend fun awaitPointerEvent(): PointerInputEvent
}

// ==================
// MARK: PointerInputElement (modifier)
// ==================

/* Modifier.Element that the host (ComposeWindow) hands every relevant
   pointer event to. Owns a PointerInputScope whose suspending block is
   driven by a LaunchedEffect tied to the user-supplied key(s). */
/* The .scope field is public to give the host module (:window) the
   ability to dispatch pointer events into it. App code should not poke
   at the scope directly — interact with it via the suspending block
   you pass to Modifier.pointerInput. */
class PointerInputElement(val scope: PointerInputScopeImpl) : Modifier.Element

/* Concrete PointerInputScope held by every PointerInputElement.
   Exposes deliverChange() for the renderer host to push events in.
   Public for cross-module visibility (Kotlin's `internal` is
   per-module, and :window needs to call deliverChange). */
class PointerInputScopeImpl : PointerInputScope {

	// One in-flight awaiter at a time — pointerInput { } blocks are
	// strictly sequential (matches upstream semantics for a single
	// awaitPointerEventScope coroutine).
	private var fAwaiter: CompletableDeferred<PointerInputEvent>? = null
	private var fLastChange: PointerInputChange? = null

	override suspend fun <R> awaitPointerEventScope(
		inBlock: suspend AwaitPointerEventScope.() -> R,
	): R {
		val vScope = object : AwaitPointerEventScope {
			override suspend fun awaitPointerEvent(): PointerInputEvent {
				val vDeferred = CompletableDeferred<PointerInputEvent>()
				fAwaiter = vDeferred
				try {
					return vDeferred.await()
				} catch (t: CancellationException) {
					if (fAwaiter === vDeferred) fAwaiter = null
					throw t
				}
			}
		}
		return vScope.inBlock()
	}

	/* Deliver a change from the host. Computes pressed-transition fields
	   off the LAST change we delivered. If nothing is awaiting, the
	   event is dropped — same as upstream when no suspension is active.
	   Called by the renderer host (:window) — not API for app code. */
	fun deliverChange(inPos: Offset, inPressed: Boolean, inId: Long) {
		val vPrev = fLastChange
		val vChange = PointerInputChange(
			id = inId,
			position = inPos,
			pressed = inPressed,
			previousPosition = vPrev?.position ?: inPos,
			previousPressed = vPrev?.pressed ?: false,
		)
		fLastChange = vChange
		val vA = fAwaiter
		fAwaiter = null
		vA?.complete(PointerInputEvent(listOf(vChange)))
	}
}
