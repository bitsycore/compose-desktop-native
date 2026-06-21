package androidx.compose.foundation.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// ==================
// MARK: Tap / press gesture helpers
// ==================

/* Constants in milliseconds — match upstream defaults. */
private const val kLongPressTimeoutMs: Long = 500L
private const val kDoubleTapTimeoutMs: Long = 300L

/* Wait until at least one pointer enters the pressed state. */
suspend fun AwaitPointerEventScope.awaitFirstDown(): PointerInputChange {
	var vChange: PointerInputChange
	do {
		vChange = awaitPointerEvent().changes.first()
	} while (!(vChange.pressed && !vChange.previousPressed))
	return vChange
}

/* Wait for the next release of the pointer that was previously pressed.
   Returns null on cancellation (composition disposed, etc). */
suspend fun AwaitPointerEventScope.waitForUpOrCancellation(): PointerInputChange? {
	while (true) {
		val vChange = awaitPointerEvent().changes.first()
		if (!vChange.pressed && vChange.previousPressed) return vChange
		if (!vChange.pressed) return null
	}
}

// ==================
// MARK: detectTapGestures
// ==================

/* Composite tap gesture: press, optional double-tap, optional
   long-press. onTap fires on a clean single-press-release. onDoubleTap
   fires on a second press within kDoubleTapTimeoutMs. onLongPress fires
   if the pointer stays pressed for kLongPressTimeoutMs without
   movement. Position passed to each callback is in node-local
   coordinates. */
suspend fun PointerInputScope.detectTapGestures(
	onPress: (suspend (Offset) -> Unit)? = null,
	onDoubleTap: ((Offset) -> Unit)? = null,
	onLongPress: ((Offset) -> Unit)? = null,
	onTap: ((Offset) -> Unit)? = null,
) {
	while (true) {
		awaitPointerEventScope {
			val vDown = awaitFirstDown()
			onPress?.invoke(vDown.position)

			// Long-press race: wait either for an UP or for the timeout.
			val vUp = withTimeoutOrNull(kLongPressTimeoutMs) { waitForUpOrCancellation() }
			if (vUp == null) {
				// Timed out → fire onLongPress, drain until release.
				onLongPress?.invoke(vDown.position)
				while (true) {
					val vCh = awaitPointerEvent().changes.first()
					if (!vCh.pressed) break
				}
				return@awaitPointerEventScope
			}

			// Got a release within the long-press window — candidate tap.
			val vTapPos = vUp.position

			// Watch for a second down inside the double-tap window.
			if (onDoubleTap != null) {
				val vSecond = withTimeoutOrNull(kDoubleTapTimeoutMs) {
					awaitFirstDown()
				}
				if (vSecond != null) {
					onDoubleTap.invoke(vSecond.position)
					// Drain the second tap's release so we don't reentrant-fire.
					waitForUpOrCancellation()
					return@awaitPointerEventScope
				}
			}
			onTap?.invoke(vTapPos)
		}
	}
}
