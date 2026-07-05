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

// ==================
// MARK: Scrollable — native actuals (mouse-wheel config + platform fling)
// ==================

/* Native actuals for the vendored upstream `Scrollable.kt` `expect`s. The desktop originals
   (DesktopScrollable.desktop.kt) are AWT-based (java.awt.MouseWheelEvent), so we provide a small
   SDL-appropriate config here: each wheel notch (scrollDelta unit) scrolls a fixed number of dp.
   The wheel PointerInputEvent is synthesized in PointerEventBridge.native.kt (feedScrollToProcessor)
   with scrollDelta already sign-corrected for SDL. */

private const val kPixelsPerWheelNotchDp = 64f

private object Sdl3ScrollConfig : ScrollConfig {
	// Immediate (non-animated) wheel scroll — the smooth path animates via withFrameNanos, which
	// needs a MonotonicFrameClock in the node coroutine scope (see ComposeOwner.coroutineContext).
	override val isSmoothScrollingEnabled: Boolean = false

	override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset {
		val vTotal = event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
		val vPx = kPixelsPerWheelNotchDp.dp.toPx()
		return Offset(vTotal.x * vPx, vTotal.y * vPx)
	}
}

internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig = Sdl3ScrollConfig

internal actual fun platformScrollableDefaultFlingBehavior(): ScrollableDefaultFlingBehavior =
	DefaultFlingBehavior(splineBasedDecay(Density(1f)))

@Composable
internal actual fun rememberPlatformDefaultFlingBehavior(): FlingBehavior {
	val vDensity = LocalDensity.current
	val vDecay = remember(vDensity) { splineBasedDecay<Float>(vDensity) }
	return remember(vDecay) { DefaultFlingBehavior(vDecay) }
}
