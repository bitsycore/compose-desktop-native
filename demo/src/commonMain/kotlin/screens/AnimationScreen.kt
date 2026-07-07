package screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: AnimationScreen
// ==================

/* Exercises the androidx.compose.animation.core surface: animate*AsState
   on Float / Dp / Int / Color, plus rememberInfiniteTransition for
   looping animations. */
@Composable
internal fun AnimationScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary
	val vOnSurface = MaterialTheme.colorScheme.onSurface

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"animation.core",
			"animate*AsState, AnimationSpec (tween / spring / repeatable / infiniteRepeatable), " +
				"Easing (Linear, FastOutSlowIn, CubicBezier), rememberInfiniteTransition. " +
				"All driven by the SDL3FrameClock via withFrameNanos.",
		)

		// ============
		//  animateDpAsState — toggle between two sizes
		var vBig by remember { mutableStateOf(false) }
		val vSizeDp by animateDpAsState(
			targetValue = if (vBig) 96.dp else 32.dp,
			animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
		)
		Section("animateDpAsState (tween 500ms, FastOutSlowIn)", "Click the box to toggle.") {
			Box(
				modifier = Modifier
					.size(vSizeDp)
					.background(vPrimary, RoundedCornerShape(8.dp))
					.clickable { vBig = !vBig },
			) {}
		}

		// ============
		//  animateColorAsState — toggle background colour
		var vAlt by remember { mutableStateOf(false) }
		val vBg by animateColorAsState(
			targetValue = if (vAlt) vSecondary else vPrimary,
			animationSpec = tween(durationMillis = 400),
		)
		Section("animateColorAsState (tween 400ms)", "Click the bar to swap colours.") {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(40.dp)
					.background(vBg, RoundedCornerShape(8.dp))
					.clickable { vAlt = !vAlt },
			) {}
		}

		// ============
		//  animateFloatAsState — spring
		var vShifted by remember { mutableStateOf(false) }
		val vOffset by animateFloatAsState(
			targetValue = if (vShifted) 200f else 0f,
			animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
		)
		Section("animateFloatAsState (spring, mediumBouncy)", "Click anywhere on the bar.") {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(40.dp)
					.clickable { vShifted = !vShifted },
			) {
				Box(
					modifier = Modifier
						// offset (not padding): a bouncy spring overshoots past its
						// target, so vOffset dips below 0 on the return — padding rejects
						// negatives ("Padding must be non-negative"), offset shows the bounce.
						.offset(x = vOffset.dp)
						.size(40.dp)
						.background(vSecondary, RoundedCornerShape(8.dp)),
				) {}
			}
		}

		// ============
		//  animateIntAsState — counter
		var vBumped by remember { mutableStateOf(0) }
		val vTarget by animateIntAsState(
			targetValue = vBumped,
			animationSpec = tween(durationMillis = 700),
		)
		Section("animateIntAsState (tween 700ms)", "Each click bumps the target by 25; the value tweens.") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Box(
					modifier = Modifier
						.size(60.dp, 40.dp)
						.background(vPrimary, RoundedCornerShape(6.dp))
						.clickable { vBumped += 25 },
				) {
					Box(modifier = Modifier.padding(8.dp)) {
						Text("+25", color = Color(0xFF000000), fontSize = 16.sp)
					}
				}
				Text("value = $vTarget", color = vOnSurface, fontSize = 16.sp)
			}
		}

		// ============
		//  InfiniteTransition — rotation + pulse
		val vTrans = rememberInfiniteTransition()
		val vAngle by vTrans.animateFloat(
			initialValue = 0f, targetValue = 360f,
			animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
		)
		val vPulse by vTrans.animateFloat(
			initialValue = 0.6f, targetValue = 1f,
			animationSpec = infiniteRepeatable(
				tween(durationMillis = 800, easing = FastOutSlowInEasing),
				repeatMode = RepeatMode.Reverse,
			),
		)
		Section(
			"rememberInfiniteTransition",
			"Two looping animations on a shared clock: a constant rotation (LinearEasing, 1800ms) and " +
				"a Reverse-mode pulse (FastOutSlowIn, 800ms).",
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
				Box(modifier = Modifier
					.size(64.dp)
					.graphicsLayer(rotationZ = vAngle)
				) {
					Canvas(modifier = Modifier.size(64.dp)) {
						drawRect(color = vPrimary, topLeft = Offset(8f, 8f), size = Size(48f, 48f))
					}
				}
				Box(modifier = Modifier
					.size(64.dp)
					.graphicsLayer(scaleX = vPulse, scaleY = vPulse)
				) {
					Canvas(modifier = Modifier.size(64.dp)) {
						drawCircle(color = vSecondary, radius = 28f)
					}
				}
			}
		}
	}
}
