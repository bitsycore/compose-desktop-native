package com.compose.sdl.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerType
import com.compose.sdl.node.impl.ComposeOwner

// ==================
// MARK: feedPointerToProcessor — native actual
// ==================

/* Held-button state across events. Upstream's `isChangedToDown` (TapGestureDetector) rejects a
   mouse down unless `buttons.isPrimaryPressed`, and drag/scroll gestures need the held button to
   persist across Move events, so the synthesized PointerInputEvent must carry a live button mask —
   not just the changed button. A single mouse => module-level flags are enough. */
private var fPrimaryDown = false
private var fSecondaryDown = false
private var fTertiaryDown = false

/* Builds the internal PointerInputEvent (its constructor is native-only) from a single mouse
   pointer and drives the owner's PointerInputEventProcessor. inType: 0=Move 1=Press 2=Release;
   inButton: 0=primary 1=secondary 2=tertiary. */
internal actual fun feedPointerToProcessor(
	inOwner: ComposeOwner,
	inType: Int,
	inButton: Int,
	inUptime: Long,
	inX: Float,
	inY: Float,
) {
	when (inType) {
		1 -> when (inButton) { 0 -> fPrimaryDown = true; 1 -> fSecondaryDown = true; 2 -> fTertiaryDown = true }
		2 -> when (inButton) { 0 -> fPrimaryDown = false; 1 -> fSecondaryDown = false; 2 -> fTertiaryDown = false }
	}

	val vButtons = PointerButtons(
		isPrimaryPressed = fPrimaryDown,
		isSecondaryPressed = fSecondaryDown,
		isTertiaryPressed = fTertiaryDown,
	)
	val vButton = if (inType == 1 || inType == 2) {
		when (inButton) {
			1 -> PointerButton.Secondary
			2 -> PointerButton.Tertiary
			else -> PointerButton.Primary
		}
	} else null

	val vPos = Offset(inX, inY)
	val vData = PointerInputEventData(
		id = PointerId(0L),
		uptime = inUptime,
		positionOnScreen = vPos,
		position = vPos,
		down = fPrimaryDown || fSecondaryDown || fTertiaryDown,
		pressure = 1f,
		type = PointerType.Mouse,
		activeHover = inType == 0,
		scaleGestureFactor = 1f,
		panGestureOffset = Offset.Zero,
	)
	val vType = when (inType) {
		1 -> PointerEventType.Press
		2 -> PointerEventType.Release
		else -> PointerEventType.Move
	}
	inOwner.processPointerInput(
		PointerInputEvent(vType, inUptime, listOf(vData), buttons = vButtons, button = vButton)
	)
}

// ==================
// MARK: feedScrollToProcessor — native actual
// ==================

/* Builds a Scroll-type PointerInputEvent carrying scrollDelta and drives the processor. The
   vendored Modifier.scrollable (MouseWheelScrollingLogic) consumes it. SDL wheel delta maps
   directly to scrollDelta (wheel-down = SDL deltaY<0 → content scrolls down); magnitude is scaled
   in Sdl3ScrollConfig (dp per notch). */
internal actual fun feedScrollToProcessor(
	inOwner: ComposeOwner,
	inX: Float,
	inY: Float,
	inDeltaX: Float,
	inDeltaY: Float,
	inUptime: Long,
) {
	val vPos = Offset(inX, inY)
	val vData = PointerInputEventData(
		id = PointerId(0L),
		uptime = inUptime,
		positionOnScreen = vPos,
		position = vPos,
		down = false,
		pressure = 0f,
		type = PointerType.Mouse,
		activeHover = false,
		scrollDelta = Offset(inDeltaX, inDeltaY),
		scaleGestureFactor = 1f,
		panGestureOffset = Offset.Zero,
	)
	inOwner.processPointerInput(
		PointerInputEvent(PointerEventType.Scroll, inUptime, listOf(vData))
	)
}
