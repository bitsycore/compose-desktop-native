package androidx.compose.ui.input.pointer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException

// PointerInputChange is now vendored verbatim from upstream — see
// core/src/vendor/.../PointerEvent.kt (it's defined inside that file,
// not its own).

// ==================
// MARK: Scopes
// ==================

/* Scope passed to the suspending block of Modifier.pointerInput. Inside
   you typically open an `awaitPointerEventScope { ... }` and loop on
   `awaitPointerEvent()`. Extends Density so DP-aware code in gesture
   detectors (touch-slop / drag thresholds) can convert without an extra
   CompositionLocal lookup. */
interface PointerInputScope : Density {

	val size: IntSize get() = IntSize.Zero
	val extendedTouchPadding: Size get() = Size.Zero
	val viewConfiguration: ViewConfiguration

	suspend fun <R> awaitPointerEventScope(
		block: suspend AwaitPointerEventScope.() -> R,
	): R
}

/* The scope inside awaitPointerEventScope where you can suspend on
   pointer events. Returns the vendored upstream `PointerEvent`
   (multi-touch — list of PointerInputChange). */
interface AwaitPointerEventScope : Density {

	val size: IntSize get() = IntSize.Zero
	val extendedTouchPadding: Size get() = Size.Zero
	val currentEvent: PointerEvent
	val viewConfiguration: ViewConfiguration

	suspend fun awaitPointerEvent(pass: PointerEventPass = PointerEventPass.Main): PointerEvent

	suspend fun <T> withTimeoutOrNull(
		timeMillis: Long,
		block: suspend AwaitPointerEventScope.() -> T,
	): T? = block()

	suspend fun <T> withTimeout(
		timeMillis: Long,
		block: suspend AwaitPointerEventScope.() -> T,
	): T = block()
}

/* Upstream's PointerEventTimeoutCancellationException lives inside the
   engine-tied SuspendingPointerInputFilter.kt (expect class + nonJvm
   actual). Our reduced AwaitPointerEventScope.withTimeout default ignores
   the timeout so the exception is rarely thrown — extracted here so
   gesture detector vendor files compile. */
class PointerEventTimeoutCancellationException(time: Long) :
	CancellationException("Timed out waiting for $time ms")

// PointerInputElement / PointerInputScopeImpl (the render-bridge
// implementations) live in com.compose.desktop.native.input per FIDELITY
// relocate rule — no official upstream equivalent.
