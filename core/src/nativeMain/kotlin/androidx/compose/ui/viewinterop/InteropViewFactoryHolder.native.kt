package androidx.compose.ui.viewinterop

import androidx.compose.runtime.ComposeNodeLifecycleCallback

// ==================
// MARK: InteropViewFactoryHolder — no-op native actual
// ==================

/*
 Mirrors upstream's skikoMain actual — an open no-op holder. No interop-view
 pipeline on desktop/native, so getInteropView returns null and the lifecycle
 callbacks are no-ops. Was previously a shim in commonMain (project-only
 class); now a proper actual matching upstream's expect.
*/
@Suppress("KmpExperimentalMismatch")
internal actual open class InteropViewFactoryHolder : ComposeNodeLifecycleCallback {
	actual open fun getInteropView(): InteropView? = null
	actual override fun onDeactivate() {}
	actual override fun onRelease() {}
	actual override fun onReuse() {}
}
