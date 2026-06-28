package androidx.compose.ui.node

// ==================
// MARK: DrawModifierNode shim
// ==================

/**
 * Project shim. Referenced by vendored Modifier.kt (one import + one
 * `as DrawModifierNode` check). No DrawModifierNode instances exist in
 * Phase 1; the check falls through. Replaced in Phase 2 by the verbatim
 * vendor of `androidx/compose/ui/node/DrawModifierNode.kt` (56 lines).
 */
interface DrawModifierNode : DelegatableNode
