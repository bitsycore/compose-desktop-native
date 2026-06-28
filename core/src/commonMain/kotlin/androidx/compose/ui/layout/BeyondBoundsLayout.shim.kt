package androidx.compose.ui.layout

import androidx.compose.ui.modifier.ProvidableModifierLocal
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.DelegatableNode

// ==================
// MARK: BeyondBoundsLayout cluster shim
// ==================

/**
 * Project shim. Vendored DelegatableNode has a
 * `findNearestBeyondBoundsLayoutAncestor()` helper that returns one of
 * these. No lazy lists wire it up in Phase 1, so this stays dormant.
 */
interface BeyondBoundsLayout {

	interface BeyondBoundsScope {
		val hasMoreContent: Boolean
	}

	@kotlin.jvm.JvmInline
	value class LayoutDirection(val value: Int) {
		companion object {
			val Before = LayoutDirection(0)
			val After = LayoutDirection(1)
			val Left = LayoutDirection(2)
			val Right = LayoutDirection(3)
			val Above = LayoutDirection(4)
			val Below = LayoutDirection(5)
		}
	}

	fun <T> layout(inDirection: LayoutDirection, inBlock: BeyondBoundsScope.() -> T?): T?
}

/**
 * Project shim. `is BeyondBoundsLayoutProviderModifierNode` checks in
 * vendored DelegatableNode fall through (no instances in Phase 1).
 */
interface BeyondBoundsLayoutProviderModifierNode : DelegatableNode {
	val beyondBoundsLayout: BeyondBoundsLayout?
}

/**
 * Project shim. The ModifierLocal key children read to walk to the nearest
 * provider. Default value `null`.
 */
val ModifierLocalBeyondBoundsLayout: ProvidableModifierLocal<BeyondBoundsLayout?> =
	modifierLocalOf { null }
