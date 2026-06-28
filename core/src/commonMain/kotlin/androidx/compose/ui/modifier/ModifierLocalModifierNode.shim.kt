package androidx.compose.ui.modifier

import androidx.compose.ui.node.DelegatableNode

// ==================
// MARK: ModifierLocalModifierNode + ModifierLocalMap shims
// ==================

/**
 * Project shim. DelegatableNode reads `providedValues.contains(key)` +
 * `providedValues.get(key)` in `findNearestBeyondBoundsLayoutAncestor()`.
 * Empty map in Phase 1 — contains() false, get() null.
 */
class ModifierLocalMap {

	fun contains(@Suppress("UNUSED_PARAMETER") inKey: ModifierLocal<*>): Boolean = false

	@Suppress("UNUSED_PARAMETER")
	operator fun <T> get(inKey: ModifierLocal<T>): T? = null
}

/**
 * Project shim. Referenced by `is ModifierLocalModifierNode` checks in
 * vendored DelegatableNode. No instances in Phase 1 → checks fall through.
 */
interface ModifierLocalModifierNode : ModifierLocalReadScope, DelegatableNode {

	val providedValues: ModifierLocalMap
}
