package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi

// ==================
// MARK: LocalPlatformPrefetchScheduler
// ==================

/**
 Upstream declares this in ui/platform/CompositionLocals.skiko.kt (which we don't vendor — it's
 platform-scene heavy) with an `error("not present")` default. Lazy layout reads it via
 `rememberDefaultPrefetchScheduler`. We give it a no-op scheduler default: prefetch is a
 background performance optimization the SDL single-threaded main loop doesn't drive, so lazy
 lists just skip ahead-of-time item composition — correctness is unaffected.
*/
@OptIn(InternalComposeUiApi::class)
val LocalPlatformPrefetchScheduler = staticCompositionLocalOf<PlatformPrefetchScheduler> {
	object : PlatformPrefetchScheduler {
		override fun scheduleHighPriorityPrefetch(request: PlatformPrefetchRequest) {}
		override fun scheduleLowPriorityPrefetch(request: PlatformPrefetchRequest) {}
	}
}
