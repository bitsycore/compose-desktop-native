@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package androidx.compose.ui.viewinterop

// Phase 9 stub — upstream holder that instantiates interop platform views.
class InteropViewFactoryHolder {
	fun getInteropView(): InteropView? = null
	fun onReuse() {}
	fun onDeactivate() {}
	fun onRelease() {}
}
