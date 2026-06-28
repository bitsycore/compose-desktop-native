package androidx.compose.ui.node

// ==================
// MARK: LayoutNode shim
// ==================

/**
 * Project shim: typealias the `androidx.compose.ui.node.LayoutNode` symbol
 * (vendored DelegatableNode and other engine files reference it without an
 * explicit import via same-package resolution) to our project's
 * `com.compose.desktop.native.node.LayoutNode`.
 *
 * Avoids needing to vendor upstream LayoutNode (~2000 lines, deeply coupled
 * to NodeCoordinator + Owner + the entire node engine) just to unblock
 * Phase 1. Our LayoutNode is extended with the minimum surface DelegatableNode
 * reads: nodes / owner / density / layoutDirection / invalidate* /
 * requestAutofill, plus `: SemanticsInfo` so the required cast type-checks.
 *
 * Delete in Phase 4 when our project LayoutNode is replaced by upstream's.
 */
internal typealias LayoutNode = com.compose.desktop.native.node.LayoutNode
