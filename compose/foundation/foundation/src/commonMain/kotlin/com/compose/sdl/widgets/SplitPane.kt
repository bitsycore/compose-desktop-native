package com.compose.sdl.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: SplitPane
// ==================

/** Two-pane resizable split. `initialFirstSize` seeds the first pane's width
 *  (horizontal split) or height (vertical split); after first layout it tracks
 *  the user's drag. `minFirstSize` / `minSecondSize` clamp so neither pane can
 *  shrink past its minimum.
 *
 *  Internal sizing is in **pixels** to match this port's pixel-space layout
 *  tree (Option-B density flow). `Modifier.width((px / density).dp)` converts
 *  back at the modifier boundary. */
@Composable
fun HorizontalSplitPane(
	modifier: Modifier = Modifier,
	initialFirstSize: Dp = 200.dp,
	minFirstSize: Dp = 50.dp,
	minSecondSize: Dp = 50.dp,
	dividerColor: Color = Color(0x33FFFFFFL),
	dividerHoverColor: Color = Color(0x66FFFFFFL),
	first: @Composable () -> Unit,
	second: @Composable () -> Unit,
) {
	val vDensity = LocalDensity.current
	val vDividerPx = with(vDensity) { SplitPaneDefaults.DividerThickness.toPx().toInt() }
	val vMinFirstPx = with(vDensity) { minFirstSize.toPx().toInt() }
	val vMinSecondPx = with(vDensity) { minSecondSize.toPx().toInt() }
	val vInitialFirstPx = with(vDensity) { initialFirstSize.toPx().toInt() }

	var vTotalWidthPx by remember { mutableStateOf(0) }
	var vFirstWidthPx by remember { mutableStateOf(vInitialFirstPx) }
	val vDividerSource = remember { MutableInteractionSource() }
	val vDividerHover by vDividerSource.collectIsHoveredAsState()
	var vDragging by remember { mutableStateOf(false) }

	Row(
		modifier = modifier
			.fillMaxSize()
			.onSizeChanged { vTotalWidthPx = it.width },
	) {
		val vClampedFirstPx = if (vTotalWidthPx > 0)
			vFirstWidthPx.coerceIn(vMinFirstPx, (vTotalWidthPx - vDividerPx - vMinSecondPx).coerceAtLeast(vMinFirstPx))
			else vFirstWidthPx
		val vSecondWidthPx = (vTotalWidthPx - vClampedFirstPx - vDividerPx).coerceAtLeast(0)

		Box(modifier = Modifier.width(with(vDensity) { vClampedFirstPx.toDp() }).fillMaxHeight()) { first() }

		// Solid divider that fills its own slot — what you see is exactly what
		// you can grab. Hover or an in-progress drag just changes the colour (no
		// size change → no layout shift, and no flicker when the pointer briefly
		// leaves the slot mid-drag).
		val vActive = vDividerHover || vDragging
		Box(
			modifier = Modifier
				.width(SplitPaneDefaults.DividerThickness)
				.fillMaxHeight()
				.background(if (vActive) dividerHoverColor else dividerColor)
				.hoverable(vDividerSource)
				.pointerInput(Unit) {
					detectDragGestures(
						onDragStart = { vDragging = true },
						onDragEnd = { vDragging = false },
						onDragCancel = { vDragging = false },
						onDrag = { _, delta ->
							val vMax = (vTotalWidthPx - vDividerPx - vMinSecondPx).coerceAtLeast(vMinFirstPx)
							vFirstWidthPx = (vFirstWidthPx + delta.x.toInt()).coerceIn(vMinFirstPx, vMax)
						},
					)
				},
		)

		Box(modifier = Modifier.width(with(vDensity) { vSecondWidthPx.toDp() }).fillMaxHeight()) { second() }
	}
}

@Composable
fun VerticalSplitPane(
	modifier: Modifier = Modifier,
	initialFirstSize: Dp = 150.dp,
	minFirstSize: Dp = 50.dp,
	minSecondSize: Dp = 50.dp,
	dividerColor: Color = Color(0x33FFFFFFL),
	dividerHoverColor: Color = Color(0x66FFFFFFL),
	first: @Composable () -> Unit,
	second: @Composable () -> Unit,
) {
	val vDensity = LocalDensity.current
	val vDividerPx = with(vDensity) { SplitPaneDefaults.DividerThickness.toPx().toInt() }
	val vMinFirstPx = with(vDensity) { minFirstSize.toPx().toInt() }
	val vMinSecondPx = with(vDensity) { minSecondSize.toPx().toInt() }
	val vInitialFirstPx = with(vDensity) { initialFirstSize.toPx().toInt() }

	var vTotalHeightPx by remember { mutableStateOf(0) }
	var vFirstHeightPx by remember { mutableStateOf(vInitialFirstPx) }
	val vDividerSource = remember { MutableInteractionSource() }
	val vDividerHover by vDividerSource.collectIsHoveredAsState()
	var vDragging by remember { mutableStateOf(false) }
	var vPressStartFirstPx by remember { mutableStateOf(0) }

	Column(
		modifier = modifier
			.fillMaxSize()
			.onSizeChanged { vTotalHeightPx = it.height },
	) {
		val vClampedFirstPx = if (vTotalHeightPx > 0)
			vFirstHeightPx.coerceIn(vMinFirstPx, (vTotalHeightPx - vDividerPx - vMinSecondPx).coerceAtLeast(vMinFirstPx))
			else vFirstHeightPx
		val vSecondHeightPx = (vTotalHeightPx - vClampedFirstPx - vDividerPx).coerceAtLeast(0)

		Box(modifier = Modifier.height(with(vDensity) { vClampedFirstPx.toDp() }).fillMaxWidth()) { first() }

		// Solid divider that fills its slot; hover/drag only changes the colour
		// (no size change → no layout shift, no flicker mid-drag).
		val vActive = vDividerHover || vDragging
		Box(
			modifier = Modifier
				.height(SplitPaneDefaults.DividerThickness)
				.fillMaxWidth()
				.background(if (vActive) dividerHoverColor else dividerColor)
				.hoverable(vDividerSource)
				.pointerInput(Unit) {
					detectDragGestures(
						onDragStart = { vDragging = true },
						onDragEnd = { vDragging = false },
						onDragCancel = { vDragging = false },
						onDrag = { _, delta ->
							val vMax = (vTotalHeightPx - vDividerPx - vMinSecondPx).coerceAtLeast(vMinFirstPx)
							vFirstHeightPx = (vFirstHeightPx + delta.y.toInt()).coerceIn(vMinFirstPx, vMax)
						},
					)
				},
		)

		Box(modifier = Modifier.height(with(vDensity) { vSecondHeightPx.toDp() }).fillMaxWidth()) { second() }
	}
}

object SplitPaneDefaults {
	val DividerThickness: Dp = 4.dp        // divider width = hit/drag area (solid; what you see is what you grab)
}
