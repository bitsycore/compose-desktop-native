package androidx.compose.foundation

// Native actual for vendored `commonMain/RequestFocusOnClick.kt`.
// Mirrors the upstream `macos.kt` / `desktop.kt` / `ios.kt` actuals which
// all return `true`. (Upstream has no `nativeMain` / `skikoMain` actual —
// the desktop targets fold in via per-platform source sets we don't
// replicate, so this hand-written stub stands in for the whole native
// family. Drop in favour of `vendor/native/...` if upstream gains one.)
internal actual fun isRequestFocusOnClickEnabled(): Boolean = true
