package androidx.compose.ui.graphics

// ==================
// MARK: GraphicsContext shim
// ==================

/**
 * Project shim for upstream `androidx.compose.ui.graphics.GraphicsContext`.
 * DelegatableNode only uses it as a return type — empty marker is enough.
 * Delete in Phase 4 when the real GraphicsContext + GraphicsLayer subtree
 * is vendored.
 */
interface GraphicsContext
