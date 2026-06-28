package androidx.compose.ui.node

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.BeyondBoundsLayoutProviderModifierNode
import androidx.compose.ui.modifier.ModifierLocalModifierNode

// ==================
// MARK: AnchorNode shim
// ==================

/**
 * Project shim. Kotlin K2 errors out with "Check for instance is always
 * 'false'" when the vendored DelegatableNode does `it is FooModifierNode`
 * for a `Modifier.Node` receiver — because none of the *ModifierNode
 * shim interfaces have a concrete Modifier.Node subclass in scope, the
 * compiler proves the type relationship is unreachable.
 *
 * This abstract anchor class makes that relationship reachable: it
 * extends Modifier.Node and implements every interface that vendored
 * DelegatableNode tests for. It is never instantiated; its sole purpose
 * is to keep the `is` checks well-typed.
 *
 * Delete in Phase 2 / 3 when the real *ModifierNode types get vendored
 * and downstream impls start to appear.
 */
@Suppress("unused")
internal abstract class AnchorNode :
	Modifier.Node(),
	DrawModifierNode,
	LayoutModifierNode,
	BeyondBoundsLayoutProviderModifierNode,
	ModifierLocalModifierNode {

	abstract override val beyondBoundsLayout: androidx.compose.ui.layout.BeyondBoundsLayout?
	abstract override val providedValues: androidx.compose.ui.modifier.ModifierLocalMap
}
