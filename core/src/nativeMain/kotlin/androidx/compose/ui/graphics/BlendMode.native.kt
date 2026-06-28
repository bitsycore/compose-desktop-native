package androidx.compose.ui.graphics

// Actual for the BlendMode.isSupported() expect in the vendored
// commonMain/Color/BlendMode set. Upstream's skiko actual lives in
// SkiaBackedPaint.skiko.kt mixed in with a pile of Skia-tied actuals we
// don't want to pull in wholesale; here we just say "yes, supported"
// the same way upstream does, since both renderer backends accept the
// full BlendMode enum (Skia maps it directly, SDL3 ignores blend mode
// — it's accept-and-ignore there).
actual fun BlendMode.isSupported(): Boolean = true
