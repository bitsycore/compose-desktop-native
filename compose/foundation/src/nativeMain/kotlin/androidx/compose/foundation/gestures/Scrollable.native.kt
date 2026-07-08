package androidx.compose.foundation.gestures

import androidx.compose.animation.splineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import kotlin.math.abs
import kotlin.math.roundToInt

// ==================
// MARK: Scrollable — native actuals (mouse-wheel config + platform fling)
// ==================

/* Native actuals for the vendored upstream `Scrollable.kt` `expect`s. Mirrors upstream's macOS
   ScrollConfig (DesktopScrollable.desktop.kt → MacOSCocoaConfig): 10dp per scrollDelta unit,
   smooth scrolling ON so mouse-wheel clicks tween over ~100ms instead of jumping instantly, and
   precise-wheel detection so trackpads (fractional deltas at 60Hz) bypass the animation and feel
   responsive. Without smooth scrolling every wheel click jumps kPixelsPerWheelNotchDp px on one
   frame — the perceived jerkiness the JVM version doesn't have. */

// Matches upstream MacOSCocoaConfig — one wheel unit = 10dp. Tuned against Cocoa; the animated
// path smooths the discrete jump so absolute magnitude doesn't need to be large.
private const val kPixelsPerWheelNotchDp = 10f

private object Sdl3ScrollConfig : ScrollConfig {
	// Animate mouse-wheel deltas over ~100ms (MouseWheelScrollingLogic's tween). The animation
	// clock derives from ComposeOwner.coroutineContext, which now carries the BroadcastFrameClock
	// pumped each frame by ComposeRootHost.sendAnimationFrame — so withFrameNanos resumes.
	override val isSmoothScrollingEnabled: Boolean = true

	// Trackpads emit fractional deltas at 60Hz; each animated 100ms would cancel the previous, so
	// upstream's `shouldApplyImmediately` bypass keeps them responsive. AWT surfaces this as
	// `preciseWheelRotation != wheelRotation`; SDL doesn't tag events, so we heuristically detect
	// "precise" by non-integer or sub-unit magnitude — regular mouse wheel emits ±1.0, ±2.0…
	override fun isPreciseWheelScroll(event: PointerEvent): Boolean {
		val vTotal = event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
		return isPrecise(vTotal.x) || isPrecise(vTotal.y)
	}

	private fun isPrecise(inV: Float): Boolean {
		if (inV == 0f) return false
		val vAbs = abs(inV)
		if (vAbs < 1f) return true
		return abs(vAbs - vAbs.roundToInt()) > 0.001f
	}

	override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset {
		val vTotal = event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
		val vPx = kPixelsPerWheelNotchDp.dp.toPx()
		return Offset(vTotal.x * vPx, vTotal.y * vPx)
	}
}

internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig = Sdl3ScrollConfig

// Fallback for AbstractScrollableNode.defaultFlingBehavior (used when the caller provides no
// flingBehavior). Stays a ScrollableDefaultFlingBehavior so updateDensity keeps working.
internal actual fun platformScrollableDefaultFlingBehavior(): ScrollableDefaultFlingBehavior =
	DefaultFlingBehavior(splineBasedDecay(Density(1f)))

/* Wraps a DefaultFlingBehavior in a plain FlingBehavior so upstream's private
   `shouldBeTriggeredByMouseWheel = this !is ScrollableDefaultFlingBehavior` check returns TRUE —
   meaning mouse-wheel + trackpad scrolls trigger a spline-decay fling after their velocity is
   tracked (see MouseWheelScrollingLogic.dispatchMouseWheelScroll → onScrollStopped(velocity)).
   Upstream JVM Compose Desktop uses DefaultFlingBehavior directly, which SKIPS the fling on
   wheel — the "velocity feel" there comes from macOS OS momentum events flowing through AWT.
   SDL doesn't reliably forward that momentum through SDL_MOUSEWHEEL on every platform, so we
   opt into fling explicitly. */
private class Sdl3WheelFlingBehavior(private val fInner: DefaultFlingBehavior) : FlingBehavior {
	override suspend fun ScrollScope.performFling(initialVelocity: Float): Float =
		with(fInner) { performFling(initialVelocity) }
}

@Composable
internal actual fun rememberPlatformDefaultFlingBehavior(): FlingBehavior {
	val vDensity = LocalDensity.current
	val vDecay = remember(vDensity) { splineBasedDecay<Float>(vDensity) }
	val vInner = remember(vDecay) { DefaultFlingBehavior(vDecay) }
	return remember(vInner) { Sdl3WheelFlingBehavior(vInner) }
}
