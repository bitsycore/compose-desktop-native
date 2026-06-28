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
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

// ==================
// MARK: PointerInputElement (modifier)
// ==================

/**
 * `ModifierNodeElement` factory for the project's pointerInput modifier.
 *
 * The host ([com.compose.desktop.native.ComposeWindow]) walks the tree with
 * `Modifier.foldIn` (this class IS-A `Modifier.Element` via
 * [ModifierNodeElement]) to find each element, then routes pointer events
 * into its [scope]. `scope` is public so `:window` can call `deliverChange`
 * across module boundaries.
 *
 * Equality is identity-on-scope: two elements with the same underlying
 * PointerInputScopeImpl are equivalent (the scope itself is unique per
 * `Modifier.pointerInput { … }` call site via `remember`).
 */
class PointerInputElement(val scope: PointerInputScopeImpl) :
	ModifierNodeElement<PointerInputNode>() {
	override fun create() = PointerInputNode(scope)
	override fun update(node: PointerInputNode) { node.scope = scope }
	override fun hashCode(): Int = scope.hashCode()
	override fun equals(other: Any?): Boolean =
		other is PointerInputElement && other.scope === scope
}

/** Paired `Modifier.Node` for [PointerInputElement]. Lifecycle dormant until the renderer rewrite drives it. */
class PointerInputNode(var scope: PointerInputScopeImpl) : Modifier.Node()

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
class PointerInputScopeImpl :
	PointerInputScope,
	Density by Density(1f) {

	// One in-flight awaiter at a time — pointerInput { } blocks are
	// strictly sequential (matches upstream semantics for a single
	// awaitPointerEventScope coroutine).
	private var fAwaiter: CompletableDeferred<PointerEvent>? = null
	private var fLastChange: PointerInputChange? = null
	private var fLastEvent: PointerEvent = PointerEvent(emptyList(), null)

	override val viewConfiguration: ViewConfiguration get() = DefaultViewConfiguration

	override suspend fun <R> awaitPointerEventScope(
		block: suspend AwaitPointerEventScope.() -> R,
	): R {
		val vOuter = this
		val vScope = object :
			AwaitPointerEventScope,
			Density by vOuter {
			override val viewConfiguration: ViewConfiguration get() = vOuter.viewConfiguration
			override val currentEvent: PointerEvent get() = fLastEvent
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
		val vEvent = PointerEvent(listOf(vChange), null)
		fLastEvent = vEvent
		val vA = fAwaiter
		fAwaiter = null
		// PointerEvent ctor takes (changes, pointerInputEvent: InternalPointerEvent?);
		// we pass null since we don't host upstream's internal pipeline.
		vA?.complete(vEvent)
	}
}

/* Default ViewConfiguration values match upstream's
   AndroidViewConfiguration defaults — used when no real platform
   ViewConfiguration is plugged in via CompositionLocal. Gesture detectors
   (long-press / double-tap timeouts; touch slop) read these directly. */
private val DefaultViewConfiguration: ViewConfiguration = object : ViewConfiguration {
	override val longPressTimeoutMillis: Long = 500L
	override val doubleTapTimeoutMillis: Long = 300L
	override val doubleTapMinTimeMillis: Long = 40L
	override val touchSlop: Float = 18f
}
