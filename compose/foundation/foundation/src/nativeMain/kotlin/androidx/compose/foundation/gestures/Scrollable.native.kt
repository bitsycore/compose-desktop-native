package androidx.compose.foundation.gestures

import androidx.compose.animation.splineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFold

// ==================
// MARK: Scrollable — native actuals (mouse-wheel config + platform fling)
// ==================

/* Native actuals for the vendored upstream `Scrollable.kt` `expect`s. Matches Compose Desktop's
   WINDOWS config (DesktopScrollable.desktop.kt → WindowsWinUIConfig), which is what the app is
   compared against:

     - each wheel unit scrolls a FRACTION of the viewport (bounds / 20), not a fixed dp — this is
       what makes a wheel click move a natural amount regardless of list length;
     - smooth scrolling ON, so MouseWheelScrollingLogic tweens each accumulated delta over up to
       ~100ms instead of jumping in one frame (the "velocity" feel: spin fast and pending notches
       sum into a longer, quicker sweep);
     - wheel is NEVER treated as "precise" (Windows animates even free-spinning wheels), so every
       notch stays on the smooth path.

   The smooth animation is the whole feel — Compose Desktop does NOT fling on the wheel (its
   default FlingBehavior is a ScrollableDefaultFlingBehavior, so `shouldBeTriggeredByMouseWheel`
   is false). We match that: no synthetic wheel fling. */

// Compose Desktop's Windows formula is `delta * (bounds/20) * scrollAmount`, where scrollAmount
// is the OS "lines to scroll per notch" (default 3). AWT forwards it; SDL normalises a wheel notch
// to ±1 and drops it, so without this factor each notch scrolls ~1/3 of Compose Desktop's distance.
private const val kWheelLinesPerNotch = 3f

private object Sdl3ScrollConfig : ScrollConfig {
	// Tween accumulated wheel deltas (MouseWheelScrollingLogic). The animation clock is the
	// BroadcastFrameClock in ComposeOwner.coroutineContext, pumped each frame by
	// ComposeRootHost.sendAnimationFrame, so withFrameNanos resumes.
	override val isSmoothScrollingEnabled: Boolean = true

	// Windows treats every wheel (even free-spinning) as non-precise → always animate. (macOS
	// uses `preciseWheelRotation != wheelRotation` to bypass the tween for trackpads; on the
	// Windows target the smooth path is always what we want.)
	override fun isPreciseWheelScroll(event: PointerEvent): Boolean = false

	override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset {
		val vTotal = event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
		// WindowsWinUIConfig formula: viewport-proportional * scrollAmount (sign kept as SDL
		// delivers it — the scrollDelta arriving here is already SDL-oriented, see
		// feedScrollToProcessor).
		return Offset(
			vTotal.x * (bounds.width / 20f) * kWheelLinesPerNotch,
			vTotal.y * (bounds.height / 20f) * kWheelLinesPerNotch,
		)
	}
}

internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig = Sdl3ScrollConfig

// Fallback for AbstractScrollableNode.defaultFlingBehavior (used when the caller provides no
// flingBehavior). A ScrollableDefaultFlingBehavior so updateDensity keeps working AND so
// `shouldBeTriggeredByMouseWheel` stays false — no wheel fling, matching Compose Desktop.
internal actual fun platformScrollableDefaultFlingBehavior(): ScrollableDefaultFlingBehavior =
	DefaultFlingBehavior(splineBasedDecay(Density(1f)))

// Natural platform fling decay — identical to Compose Desktop's Scrollable.desktop.kt: a plain
// DefaultFlingBehavior (spline decay). It IS a ScrollableDefaultFlingBehavior, so touch drags
// fling but the mouse wheel does not (its smooth tween handles the wheel feel).
@Composable
internal actual fun rememberPlatformDefaultFlingBehavior(): FlingBehavior {
	val vDensity = LocalDensity.current
	val vDecay = remember(vDensity) { splineBasedDecay<Float>(vDensity) }
	return remember(vDecay) { DefaultFlingBehavior(vDecay) }
}
