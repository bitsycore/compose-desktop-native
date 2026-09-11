@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

// VENDOR-REIMPL: compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/viewinterop/InteropView.skiko.kt @ v1.12.0
// Project REIMPLEMENTATION, not a derived copy: same package + signatures,
// different body (the port replaces upstream's skiko scene/platform layer).
// Upstream edits to the counterpart do NOT need reconciling here - re-check
// only if the expect/actual SIGNATURES change.

package androidx.compose.ui.viewinterop

// Native actual for vendored commonMain InteropView.kt. Mirrors upstream
// macosMain / webMain / iosMain actuals which all resolve to Any (those
// platforms don't host a native View hierarchy). Our nativeMain serves
// macos+linux+windows and none expose interop - same shape applies.
//
// (Upstream's skikoMain actual carries the full TypedInteropViewHolder
// infrastructure built on InteropContainer / InteropViewHolder / etc.,
// none of which we have. The lighter macos-style actual is the right fit.)
actual typealias InteropView = Any
