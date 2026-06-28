package com.compose.desktop.native.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

// ==================
// MARK: PointerInputElement (modifier)
// ==================

/* Modifier.Element that the host (ComposeWindow) hands every relevant
   pointer event to. Owns a PointerInputScope whose suspending block is
   driven by a LaunchedEffect tied to the user-supplied key(s).

   .scope is public to give :window the ability to dispatch pointer events
   into it. App code should not poke at the scope directly — interact with
   it via the suspending block you pass to Modifier.pointerInput. */
class PointerInputElement(val scope: PointerInputScopeImpl) : Modifier.Element

// ==================
// MARK: PointerInputScopeImpl
// ==================

/* Concrete PointerInputScope held by every PointerInputElement.
   Exposes deliverChange() for the renderer host to push events in.
   Public for cross-module visibility (Kotlin's `internal` is per-module,
   and :window needs to call deliverChange).

   Inside `awaitPointerEvent()` we now produce upstream-shape
   PointerEvent + PointerInputChange (vendored) — uses the full 13-field
   constructor with pointerInputEvent=null (we don't host upstream's
   internal pipeline). */
class PointerInputScopeImpl : PointerInputScope {

	// One in-flight awaiter at a time — pointerInput { } blocks are
	// strictly sequential (matches upstream semantics for a single
	// awaitPointerEventScope coroutine).
	private var fAwaiter: CompletableDeferred<PointerEvent>? = null
	private var fLastChange: PointerInputChange? = null

	override suspend fun <R> awaitPointerEventScope(
		block: suspend AwaitPointerEventScope.() -> R,
	): R {
		val vScope = object : AwaitPointerEventScope {
			override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent {
				val vDeferred = CompletableDeferred<PointerEvent>()
				fAwaiter = vDeferred
				try {
					return vDeferred.await()
				} catch (t: CancellationException) {
					if (fAwaiter === vDeferred) fAwaiter = null
					throw t
				}
			}
		}
		return vScope.block()
	}

	/* Deliver a change from the host. Computes pressed-transition fields
	   off the LAST change we delivered. If nothing is awaiting, the
	   event is dropped — same as upstream when no suspension is active.
	   Called by the renderer host (:window) — not API for app code. */
	fun deliverChange(position: Offset, pressed: Boolean, id: Long) {
		val vPrev = fLastChange
		val vNow = 0L  // we don't track event timestamps; gesture timing uses withFrameNanos
		val vChange = PointerInputChange(
			id = PointerId(id),
			uptimeMillis = vNow,
			position = position,
			pressed = pressed,
			pressure = 1f,
			previousUptimeMillis = vNow,
			previousPosition = vPrev?.position ?: position,
			previousPressed = vPrev?.pressed ?: false,
			isInitiallyConsumed = false,
			type = PointerType.Mouse,
		)
		fLastChange = vChange
		val vA = fAwaiter
		fAwaiter = null
		// PointerEvent ctor takes (changes, pointerInputEvent: InternalPointerEvent?);
		// we pass null since we don't host upstream's internal pipeline.
		vA?.complete(PointerEvent(listOf(vChange), null))
	}
}
