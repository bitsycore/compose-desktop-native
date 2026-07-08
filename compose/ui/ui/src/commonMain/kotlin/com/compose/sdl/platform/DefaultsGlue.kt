package com.compose.sdl.platform

import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize

// ==================
// MARK: DefaultViewConfiguration — project default (upstream has this inside
//   PlatformContext.skiko.kt, which we can't vendor because it needs ComposeScene
//   / CanvasLayersComposeScene).
// ==================

/**
 * Non-composable `ViewConfiguration` used by [Owner]-side stubs (ComposeOwner /
 * StubOwner). Constant values match the upstream `PlatformContext.DefaultViewConfiguration`
 * (longPressTimeout=500ms, doubleTapTimeout=300ms, doubleTapMinTime=40ms, touchSlop=8f).
 */
object DefaultViewConfiguration : ViewConfiguration {
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
