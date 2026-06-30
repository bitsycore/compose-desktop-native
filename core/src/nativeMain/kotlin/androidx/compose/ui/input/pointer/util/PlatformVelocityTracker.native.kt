package androidx.compose.ui.input.pointer.util

// Native actual for vendored commonMain `PlatformVelocityTracker.kt`.
// Mirrors upstream macos / desktop / ios actuals — all return
// `Lsq2VelocityTracker()` (the least-squares-2 fitter declared in the
// vendored VelocityTracker.kt). No `nativeMain` / `skikoMain` actual
// upstream so we hand-write this one.
internal actual fun PlatformVelocityTracker(): PlatformVelocityTracker = Lsq2VelocityTracker()
