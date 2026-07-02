package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize

// Phase 9 — ViewConfiguration interface is vendored; this provides a default impl
// of all its members + the CompositionLocal the vendored LayoutNode reads.
internal object DefaultViewConfiguration : ViewConfiguration {
	override val longPressTimeoutMillis: Long = 500
	override val doubleTapTimeoutMillis: Long = 300
	override val doubleTapMinTimeMillis: Long = 40
	override val touchSlop: Float = 8f
	override val handwritingSlop: Float = 2f
	override val minimumTouchTargetSize: DpSize = DpSize.Zero
	override val maximumFlingVelocity: Float = Float.MAX_VALUE
	override val minimumFlingVelocity: Float = 0f
	override val handwritingGestureLineMargin: Float = 0f
}

val LocalViewConfiguration = staticCompositionLocalOf<ViewConfiguration> { DefaultViewConfiguration }
