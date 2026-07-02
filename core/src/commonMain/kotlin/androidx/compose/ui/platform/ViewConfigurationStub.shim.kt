package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

// Phase 9 stub — upstream ViewConfiguration (gesture timings / touch slop).
interface ViewConfiguration {
	val longPressTimeoutMillis: Long
	val doubleTapTimeoutMillis: Long
	val doubleTapMinTimeMillis: Long
	val touchSlop: Float
}

val LocalViewConfiguration = staticCompositionLocalOf<ViewConfiguration> {
	object : ViewConfiguration {
		override val longPressTimeoutMillis = 500L
		override val doubleTapTimeoutMillis = 300L
		override val doubleTapMinTimeMillis = 40L
		override val touchSlop = 8f
	}
}
