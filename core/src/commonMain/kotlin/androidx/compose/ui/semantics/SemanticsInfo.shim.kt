package androidx.compose.ui.semantics

import androidx.compose.ui.layout.LayoutInfo

// ==================
// MARK: SemanticsInfo shim
// ==================

/**
 * Project shim. Vendored DelegatableNode only uses it as a cast target in
 *
 *     internal fun DelegatableNode.requireSemanticsInfo(): SemanticsInfo
 *         = requireLayoutNode()
 *
 * Upstream LayoutNode implements SemanticsInfo. Our project LayoutNode
 * marks itself with this shim so the cast type-checks; no member access
 * happens in Phase 1.
 *
 * Delete when the real SemanticsInfo + SemanticsConfiguration chain is
 * vendored (its own multi-session sprint — paragraph engine + autofill).
 */
internal interface SemanticsInfo : LayoutInfo
