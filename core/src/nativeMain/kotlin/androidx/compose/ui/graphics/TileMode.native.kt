package androidx.compose.ui.graphics

// Native actual for the vendored TileMode.isSupported() expect. The upstream
// actuals are platform-specific (Skiko / Android); our renderers map every
// TileMode themselves, so all modes are reported supported (same as Skiko's).
actual fun TileMode.isSupported(): Boolean = true
