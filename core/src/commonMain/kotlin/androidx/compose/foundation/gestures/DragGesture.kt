package androidx.compose.foundation.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

// ==================
// MARK: detectDragGestures
// ==================

/* A few-pixel touch slop before motion counts as a drag — keeps a
   slightly wobbly tap from firing onDragStart. Matches upstream's
   default touch-slop (we drop the density distinction for simplicity). */
private const val kTouchSlop: Float = 6f

/* Drag-after-press gesture detector:
   - Press registers, but no callback fires yet.
   - First motion that exceeds touch slop fires onDragStart with the
     pre-slop position.
   - Each subsequent move fires onDrag with the per-frame delta.
   - Release fires onDragEnd.
   - If the pointer is released BEFORE motion exceeds slop, no drag
     callbacks fire (it was a tap, not a drag). */
suspend fun PointerInputScope.detectDragGestures(
	onDragStart: ((Offset) -> Unit)? = null,
	onDragEnd: (() -> Unit)? = null,
	onDragCancel: (() -> Unit)? = null,
	onDrag: ((change: PointerInputChange, dragAmount: Offset) -> Unit)? = null,
) {
	while (true) {
		awaitPointerEventScope {
			val vDown = awaitFirstDown()
			var vTotalX = 0f
			var vTotalY = 0f
			var vDragging = false

			while (true) {
				val vCh = awaitPointerEvent().changes.first()
				if (!vCh.pressed) {
					if (vDragging) onDragEnd?.invoke()
					return@awaitPointerEventScope
				}
				val vDx = vCh.position.x - vCh.previousPosition.x
				val vDy = vCh.position.y - vCh.previousPosition.y
				vTotalX += vDx
				vTotalY += vDy
				if (!vDragging) {
					if (abs(vTotalX) > kTouchSlop || abs(vTotalY) > kTouchSlop) {
						vDragging = true
						onDragStart?.invoke(vCh.position)
					}
				} else {
					onDrag?.invoke(vCh, Offset(vDx, vDy))
				}
			}
		}
	}
}
