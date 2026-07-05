@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package androidx.compose.ui.viewinterop

// Native actual for vendored commonMain InteropView.kt. Mirrors upstream
// macosMain / webMain / iosMain actuals which all resolve to Any (those
// platforms don't host a native View hierarchy). Our nativeMain serves
// macos+linux+windows and none expose interop — same shape applies.
//
// (Upstream's skikoMain actual carries the full TypedInteropViewHolder
// infrastructure built on InteropContainer / InteropViewHolder / etc.,
// none of which we have. The lighter macos-style actual is the right fit.)
actual typealias InteropView = Any
