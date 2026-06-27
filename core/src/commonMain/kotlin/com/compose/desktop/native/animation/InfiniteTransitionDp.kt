package com.compose.desktop.native.animation

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: InfiniteTransition.animateDp
// ==================

/* Dp leg of an InfiniteTransition. Non-official convenience — official Compose
   animates Dp through animateValue + a TwoWayConverter, which this lerp-lambda
   stand-in deliberately omits, so this lives in the com.compose.desktop.native
   layer rather than androidx.compose.animation. */
@Composable
fun InfiniteTransition.animateDp(
	initialValue: Dp,
	targetValue: Dp,
	animationSpec: InfiniteRepeatableSpec<Dp>,
	label: String = "DpAnimation",
): State<Dp> {
	val vState = remember { mutableStateOf(initialValue) }
	remember(initialValue, targetValue) {
		add(InfiniteTransition.Child(vState, initialValue, targetValue, animationSpec) { vA, vB, vF -> (vA.value + (vB.value - vA.value) * vF).dp })
	}
	return vState
}
