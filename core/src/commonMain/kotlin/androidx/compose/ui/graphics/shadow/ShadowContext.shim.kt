package androidx.compose.ui.graphics.shadow

// ==================
// MARK: ShadowContext / PlatformShadowContext — shim
// ==================

/**
 * Marker for upstream `androidx.compose.ui.graphics.shadow.ShadowContext`.
 * Real interface backs drop / inner shadow rendering.
 * `PlatformShadowContext` is the platform base. Vendored `GraphicsContext`
 * declares `val shadowContext: ShadowContext get() = object : PlatformShadowContext {}`.
 */
interface ShadowContext

interface PlatformShadowContext : ShadowContext

// Marker types for upstream `DropShadowPainter` / `InnerShadowPainter` —
// referenced by DrawModifier.kt KDoc and (through CacheDrawScope.obtainShadowContext())
// by user code that wants to paint a shadow. The full paint pipeline is unvendored
// (`shadow/Blur.kt` + `Shadow.kt` + `ShadowRenderer.kt`) so these stay markers until
// the shadow engine lands.
class DropShadowPainter
class InnerShadowPainter
