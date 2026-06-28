package androidx.compose.ui.node

// ==================
// MARK: LayoutModifierNode shim
// ==================

/**
 * Project shim. Referenced by vendored DelegatableNode in four `is
 * LayoutModifierNode` checks inside the `requireCoordinator` family. No
 * instances exist in Phase 1; checks fall through. Replaced in Phase 3 by
 * the verbatim vendor of `androidx/compose/ui/node/LayoutModifierNode.kt`.
 */
interface LayoutModifierNode : DelegatableNode
