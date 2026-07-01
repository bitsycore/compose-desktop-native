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
